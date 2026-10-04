package dev.elysium.visuals.client.alt;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elysium.visuals.ElysiumVisuals;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The account list, shared with Elysium Launcher: on Windows it is the
 * launcher's {@code %APPDATA%\ElysiumLauncher\accounts.json}, so accounts
 * added in either place show up in both. No secrets are written here.
 */
public final class AccountStore {
	private AccountStore() {
	}

	public static Path file() {
		String override = System.getProperty("elysium.accountsFile");
		if (override != null && !override.isBlank()) {
			return Path.of(override);
		}
		String appData = System.getenv("APPDATA");
		if (Util.getPlatform() == Util.OS.WINDOWS && appData != null) {
			return Path.of(appData, "ElysiumLauncher", "accounts.json");
		}
		return FabricLoader.getInstance().getConfigDir().resolve(ElysiumVisuals.MOD_ID).resolve("accounts.json");
	}

	public static synchronized List<AltAccount> load() {
		List<AltAccount> list = new ArrayList<>();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return list;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (root.isJsonArray()) {
				for (JsonElement e : root.getAsJsonArray()) {
					JsonObject o = e.getAsJsonObject();
					String id = str(o, "id"), name = str(o, "username"), uuid = str(o, "uuid");
					if (id == null || name == null || uuid == null || uuid.replace("-", "").length() != 32) {
						continue;
					}
					list.add(new AltAccount(id, AltAccount.Kind.of(str(o, "kind")), name, uuid.replace("-", ""), str(o, "skinUrl")));
				}
			}
		} catch (Exception e) {
			ElysiumVisuals.LOGGER.warn("Can't read the account list: {}", e.toString());
		}
		return list;
	}

	public static synchronized void save(List<AltAccount> accounts) {
		JsonArray arr = new JsonArray();
		for (AltAccount a : accounts) {
			JsonObject o = new JsonObject();
			o.addProperty("id", a.id());
			o.addProperty("kind", a.kind().json);
			o.addProperty("username", a.username());
			o.addProperty("uuid", a.uuid());
			if (a.skinUrl() != null) {
				o.addProperty("skinUrl", a.skinUrl());
			}
			arr.add(o);
		}
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling("accounts.json.tmp");
			Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(arr), StandardCharsets.UTF_8);
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ElysiumVisuals.LOGGER.warn("Can't save the account list: {}", e.toString());
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}
}
