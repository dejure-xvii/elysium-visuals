package dev.elysium.visuals.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.module.impl.farm.InventoryAlert;
import dev.elysium.visuals.client.module.impl.farm.AutoCraft;
import dev.elysium.visuals.client.module.impl.utils.LockSlot;
import dev.elysium.visuals.client.module.impl.utils.ChatHelper;
import dev.elysium.visuals.client.module.impl.utils.DeathCoords;
import dev.elysium.visuals.client.module.impl.render.Optimizer;
import dev.elysium.visuals.client.module.impl.farm.LootTracker;
import dev.elysium.visuals.client.module.impl.farm.SessionTimer;
import dev.elysium.visuals.client.module.impl.farm.ToolGuard;
import dev.elysium.visuals.client.module.impl.player.ArmorHud;
import dev.elysium.visuals.client.module.impl.player.AutoTool;
import dev.elysium.visuals.client.module.impl.player.AutoSprint;
import dev.elysium.visuals.client.module.impl.player.AutoEventGps;
import dev.elysium.visuals.client.module.impl.player.PvPHelper;
import dev.elysium.visuals.client.module.impl.utils.BowOptimizer;
import dev.elysium.visuals.client.module.impl.utils.CrystalOptimizer;
import dev.elysium.visuals.client.module.impl.utils.RegionHelper;
import dev.elysium.visuals.client.module.impl.player.HealthAlert;
import dev.elysium.visuals.client.module.impl.player.ItemCounter;
import dev.elysium.visuals.client.module.impl.player.NoFriendDamage;
import dev.elysium.visuals.client.module.impl.render.Crosshair;
import dev.elysium.visuals.client.module.impl.render.ChunkAnimator;
import dev.elysium.visuals.client.module.impl.render.CustomPet;
import dev.elysium.visuals.client.module.impl.render.FullBright;
import dev.elysium.visuals.client.module.impl.render.NoCameraClip;
import dev.elysium.visuals.client.module.impl.render.Shaders;
import dev.elysium.visuals.client.module.impl.utils.Zoom;
import dev.elysium.visuals.client.module.impl.utils.Gps;
import dev.elysium.visuals.client.module.impl.render.CustomCrystal;
import dev.elysium.visuals.client.module.impl.render.JumpCircle;
import dev.elysium.visuals.client.module.impl.render.Keystrokes;
import dev.elysium.visuals.client.module.impl.render.KillEffect;
import dev.elysium.visuals.client.module.impl.render.LootBeams;
import dev.elysium.visuals.client.module.impl.render.Ambience;
import dev.elysium.visuals.client.module.impl.render.AspectRatio;
import dev.elysium.visuals.client.module.impl.render.BlockOverlay;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.FireworkESP;
import dev.elysium.visuals.client.module.impl.render.Hands;
import dev.elysium.visuals.client.module.impl.render.MotionBlur;
import dev.elysium.visuals.client.module.impl.render.NoRender;
import dev.elysium.visuals.client.module.impl.render.Particles;
import dev.elysium.visuals.client.module.impl.render.Predictions;
import dev.elysium.visuals.client.module.impl.render.ShulkerPreview;
import dev.elysium.visuals.client.module.impl.render.SwingAnimation;
import dev.elysium.visuals.client.module.impl.render.TargetESP;
import dev.elysium.visuals.client.module.impl.render.Trail;
import dev.elysium.visuals.client.module.impl.render.ViewModel;
import dev.elysium.visuals.client.module.impl.render.Watermark;
import dev.elysium.visuals.client.module.impl.render.WorldInfo;
import dev.elysium.visuals.client.module.impl.utils.*;
import dev.elysium.visuals.client.module.setting.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/** Owns all modules: registration, ticking, key binds, HUD rendering and config (de)serialisation. */
public final class ModuleManager {
	private static final ModuleManager INSTANCE = new ModuleManager();

	private final List<Module> modules = new ArrayList<>();
	private boolean dirty;

	private ModuleManager() {
	}

	public static ModuleManager get() {
		return INSTANCE;
	}

	/** Add new modules here. */
	private void registerModules() {
		register(new AutoSprint());
		register(new HealthAlert());
		register(new ArmorHud());
		register(new ItemCounter());
		register(new AutoTool());
		register(new NoFriendDamage());

		register(new Watermark());
		register(new Crosshair());
		register(new Keystrokes());
		register(new WorldInfo());
		register(new Particles());
		register(new ViewModel());
		register(new SwingAnimation());
		register(new NoRender());
		register(new MotionBlur());
		register(new Ambience());
		register(new CustomSky());
		register(new BlockOverlay());
		register(new JumpCircle());
		register(new Trail());
		register(new Predictions());
		register(new TargetESP());
		register(new AspectRatio());
		register(new ShulkerPreview());
		register(new FireworkESP());
		register(new LootBeams());
		register(new Hands());
		register(new KillEffect());
		register(new CustomPet());
		register(new CustomCrystal());
		register(new ChunkAnimator());
		register(new FullBright());
		register(new NoCameraClip());
		register(new Shaders());

		register(new Coordinates());
		register(new FriendsModule());
		register(new Speedometer());
		register(new DeathPoint());
		register(new ServerInfo());
		register(new NameProtect());
		register(new AutoAccept());
		register(new ItemScroller());
		register(new ClickPearl());
		register(new ClickWeb());
		register(new ClickFirework());
		register(new ScoreboardHealth());
		register(new ClientSounds());
		register(new NotificationsModule());
		register(new UseTracker());
		register(new FakePlayer());

		register(new SessionTimer());
		register(new InventoryAlert());
		register(new ToolGuard());
		register(new LootTracker());
		register(new MainMenu());
		register(new AutoCraft());
		register(new LockSlot());
		register(new ChatHelper());
		register(new DeathCoords());
		register(new Optimizer());
		register(new Zoom());
		register(new Gps());
		register(new AutoEventGps());
		register(new PvPHelper());
		register(new BowOptimizer());
		register(new CrystalOptimizer());
		register(new RegionHelper());
	}

	public void init() {
		registerModules();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	/** The registered instance of a module class, or null. */
	public <T extends Module> T find(Class<T> type) {
		for (Module m : modules) {
			if (type.isInstance(m)) {
				return type.cast(m);
			}
		}
		return null;
	}

	private void register(Module module) {
		for (Module m : modules) {
			if (m.id().equals(module.id())) {
				throw new IllegalStateException("Duplicate module id: " + module.id());
			}
		}
		modules.add(module);
	}

	public List<Module> modules() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> byCategory(Category category) {
		List<Module> list = new ArrayList<>();
		for (Module m : modules) {
			if (m.category() == category) {
				list.add(m);
			}
		}
		return list;
	}

	private void tick(Minecraft mc) {
		// Key binds work only in-game (no screen open) and when the window is focused; never after .panic.
		boolean canUseBinds = mc.gui.screen() == null && mc.isWindowActive() && !Panic.isActive();
		for (Module m : modules) {
			if (m.bind() != Module.NO_KEY) {
				boolean down = canUseBinds && InputConstants.isKeyDown(mc.getWindow(), m.bind());
				if (m.updateBindState(down)) {
					if (m.bindIsAction()) {
						if (m.isEnabled()) {
							m.onBindPressed(mc);
						}
					} else {
						m.toggle();
					}
				}
			}
			if (m.isEnabled()) {
				m.onTick(mc);
			}
		}
	}

	// --- Toggle listeners -------------------------------------------------------

	private final List<BiConsumer<Module, Boolean>> toggleListeners = new ArrayList<>();
	private int silent;

	/** Called whenever a module is switched on or off by the user (not during config loads). */
	public void onToggle(BiConsumer<Module, Boolean> listener) {
		toggleListeners.add(listener);
	}

	/** Runs {@code body} without toggle notifications (bulk changes such as loading a config). */
	public void silently(Runnable body) {
		silent++;
		try {
			body.run();
		} finally {
			silent--;
		}
	}

	void fireToggle(Module module, boolean enabled) {
		if (silent == 0) {
			for (BiConsumer<Module, Boolean> l : toggleListeners) {
				l.accept(module, enabled);
			}
		}
	}

	// --- Config ---------------------------------------------------------------

	public void markDirty() {
		dirty = true;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void clearDirty() {
		dirty = false;
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		for (Module m : modules) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", m.isEnabled());
			obj.addProperty("bind", m.bind());
			JsonObject settings = new JsonObject();
			for (Setting<?> s : m.settings()) {
				settings.add(s.id(), s.toJson());
			}
			obj.add("settings", settings);
			root.add(m.id(), obj);
		}
		return root;
	}

	/** Every module back to its defaults (global settings such as friends are kept). */
	public void resetAll() {
		for (Module m : modules) {
			m.resetToDefaults();
		}
	}

	/**
	 * Applies a saved config while the game is running: modules not mentioned
	 * fall back to defaults, enabling/disabling runs the usual hooks, and
	 * global settings keep their current values.
	 */
	public void apply(JsonObject root) {
		for (Module m : modules) {
			m.resetSettings();
			JsonElement e = root.get(m.id());
			if (e == null || !e.isJsonObject()) {
				m.setEnabled(m.isEnabledByDefault());
				continue;
			}
			JsonObject obj = e.getAsJsonObject();
			try {
				JsonObject settings = obj.has("settings") && obj.get("settings").isJsonObject()
						? obj.getAsJsonObject("settings") : new JsonObject();
				for (Setting<?> s : m.settings()) {
					JsonElement v = settings.get(s.id());
					if (v != null && !s.isGlobal()) {
						s.fromJson(v);
					}
				}
				m.setBind(obj.has("bind") ? obj.get("bind").getAsInt() : Module.NO_KEY);
				m.setEnabled(obj.has("enabled") && obj.get("enabled").getAsBoolean());
			} catch (RuntimeException ex) {
				ElysiumVisuals.LOGGER.warn("Ignoring invalid config for module {}", m.id(), ex);
			}
		}
		dirty = true;
	}

	public void fromJson(JsonObject root) {
		for (Module m : modules) {
			JsonElement e = root.get(m.id());
			if (e == null || !e.isJsonObject()) {
				continue;
			}
			JsonObject obj = e.getAsJsonObject();
			try {
				JsonObject settings = obj.has("settings") && obj.get("settings").isJsonObject()
						? obj.getAsJsonObject("settings") : new JsonObject();
				for (Setting<?> s : m.settings()) {
					JsonElement v = settings.get(s.id());
					if (v != null) {
						s.fromJson(v);
					}
				}
				boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();
				int bind = obj.has("bind") ? obj.get("bind").getAsInt() : Module.NO_KEY;
				m.loadState(enabled, bind);
			} catch (RuntimeException ex) {
				ElysiumVisuals.LOGGER.warn("Ignoring invalid config for module {}", m.id(), ex);
			}
		}
		dirty = false;
	}
}