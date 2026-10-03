package dev.elysium.visuals.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.hud.HudManager;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.theme.ColorSlot;
import dev.elysium.visuals.client.theme.Theme;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.theme.Themes;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads and writes {@code config/elysium-visuals.json} (the live settings) and
 * named configs in {@code config/elysium-visuals/configs/<name>.json}. Both use
 * the same format:
 * <pre>
 * {
 *   "theme": "liquid_glass",
 *   "custom": { "background": "#F2141620", "accent": "#7C5CFF", ..., "glass": false },
 *   "modules": { "auto_sprint": { "enabled": true, "bind": 71, "settings": { ... } }, ... },
 *   "hud": { "watermark/watermark": [0.0, 0.0], ... }
 * }
 * </pre>
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(ElysiumVisuals.MOD_ID + ".json");
	private static final Path CONFIGS = FabricLoader.getInstance().getConfigDir().resolve(ElysiumVisuals.MOD_ID).resolve("configs");
	private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]{1,32}");

	private ConfigManager() {
	}

	// --- Live config ----------------------------------------------------------

	public static void load() {
		ThemeManager themes = ThemeManager.get();
		if (!Files.exists(FILE)) {
			save();
			return;
		}
		try {
			JsonObject root = read(FILE);
			applyTheme(root);
			if (root.has("modules") && root.get("modules").isJsonObject()) {
				ModuleManager.get().fromJson(root.getAsJsonObject("modules"));
			}
			if (root.has("hud") && root.get("hud").isJsonObject()) {
				HudManager.get().fromJson(root.getAsJsonObject("hud"));
			}
			ElysiumVisuals.LOGGER.info("Loaded config, active theme: {}", themes.active().id());
		} catch (Exception e) {
			ElysiumVisuals.LOGGER.error("Failed to read {}, using defaults", FILE, e);
			themes.load(Themes.DEFAULT.id(), null);
		}
	}

	/** Writes the live settings. Does nothing after {@code .panic}, so the hidden state is never saved. */
	public static void save() {
		if (Panic.isActive()) {
			return;
		}
		try {
			write(FILE, snapshot());
			ThemeManager.get().clearDirty();
			ModuleManager.get().clearDirty();
			HudManager.get().clearDirty();
		} catch (IOException e) {
			ElysiumVisuals.LOGGER.error("Failed to save {}", FILE, e);
		}
	}

	public static void saveIfDirty() {
		if (ThemeManager.get().isDirty() || ModuleManager.get().isDirty() || HudManager.get().isDirty()) {
			save();
		}
	}

	/** Everything that makes up a config: theme, modules (state, bind, settings) and HUD positions. */
	private static JsonObject snapshot() {
		ThemeManager themes = ThemeManager.get();
		JsonObject root = new JsonObject();
		root.addProperty("theme", themes.active().id());
		JsonObject custom = new JsonObject();
		for (ColorSlot slot : ColorSlot.values()) {
			custom.addProperty(slot.key(), ColorUtil.toHex(themes.custom().color(slot)));
		}
		custom.addProperty("glass", themes.custom().glass());
		root.add("custom", custom);
		root.add("modules", ModuleManager.get().toJson());
		root.add("hud", HudManager.get().toJson());
		return root;
	}

	private static void applyTheme(JsonObject root) {
		String themeId = root.has("theme") ? root.get("theme").getAsString() : Themes.DEFAULT.id();
		Theme custom = Themes.newCustom();
		if (root.has("custom") && root.get("custom").isJsonObject()) {
			JsonObject c = root.getAsJsonObject("custom");
			for (ColorSlot slot : ColorSlot.values()) {
				JsonElement e = c.get(slot.key());
				Integer color = e != null && e.isJsonPrimitive() ? ColorUtil.parseHex(e.getAsString()) : null;
				if (color != null) {
					custom.setColor(slot, color);
				}
			}
			if (c.has("glass")) {
				custom.setGlass(c.get("glass").getAsBoolean());
			}
		}
		ThemeManager.get().load(themeId, custom);
	}

	// --- Named configs (.cfg) -------------------------------------------------

	/** Config names are case-insensitive: stored lower-case, letters/digits/_/- only. */
	public static String normalizeName(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return NAME.matcher(n).matches() ? n : null;
	}

	private static Path named(String name) {
		return CONFIGS.resolve(name + ".json");
	}

	public static boolean exists(String name) {
		return Files.isRegularFile(named(name));
	}

	public static void saveNamed(String name) throws IOException {
		write(named(name), snapshot());
	}

	/** Replaces the live settings with a saved config. */
	public static void loadNamed(String name) throws IOException {
		JsonObject root = read(named(name));
		applyTheme(root);
		JsonObject modules = root.has("modules") && root.get("modules").isJsonObject()
				? root.getAsJsonObject("modules") : new JsonObject();
		ModuleManager.get().silently(() -> ModuleManager.get().apply(modules));
		HudManager.get().resetPositions();
		if (root.has("hud") && root.get("hud").isJsonObject()) {
			HudManager.get().fromJson(root.getAsJsonObject("hud"));
		}
		save();
	}

	public static void removeNamed(String name) throws IOException {
		Files.delete(named(name));
	}

	/** Saved config names, sorted. */
	public static List<String> listNamed() {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(CONFIGS)) {
			return names;
		}
		try (Stream<Path> files = Files.list(CONFIGS)) {
			files.map(p -> p.getFileName().toString())
					.filter(f -> f.endsWith(".json"))
					.map(f -> f.substring(0, f.length() - 5))
					.filter(n -> NAME.matcher(n).matches())
					.sorted()
					.forEach(names::add);
		} catch (IOException e) {
			ElysiumVisuals.LOGGER.warn("Failed to list {}", CONFIGS, e);
		}
		return names;
	}

	/** Everything back to defaults (theme, modules, binds, HUD positions); the friend list is kept. */
	public static void resetAll() {
		ThemeManager.get().load(Themes.DEFAULT.id(), Themes.newCustom());
		ModuleManager.get().silently(ModuleManager.get()::resetAll);
		HudManager.get().resetPositions();
		save();
	}

	// --- Files ----------------------------------------------------------------

	private static JsonObject read(Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IllegalStateException | com.google.gson.JsonParseException e) {
			throw new IOException("Invalid config " + file, e);
		}
	}

	private static void write(Path file, JsonObject root) throws IOException {
		Files.createDirectories(file.getParent());
		// Write to a temp file first so a crash mid-write can't corrupt the config.
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
	}
}
