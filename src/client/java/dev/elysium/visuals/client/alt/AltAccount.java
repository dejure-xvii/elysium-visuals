package dev.elysium.visuals.client.alt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * An account in the shared list ({@code accounts.json} of Elysium Launcher).
 * Holds public data only; refresh tokens live in {@link SecretStore}.
 *
 * @param uuid profile UUID without dashes
 */
public record AltAccount(String id, Kind kind, String username, String uuid, String skinUrl) {
	public enum Kind {
		MICROSOFT("microsoft"), OFFLINE("offline");

		final String json;

		Kind(String json) {
			this.json = json;
		}

		static Kind of(String s) {
			return "microsoft".equalsIgnoreCase(s) ? MICROSOFT : OFFLINE;
		}
	}

	public boolean microsoft() {
		return kind == Kind.MICROSOFT;
	}

	public UUID profileId() {
		return parseUuid(uuid);
	}

	public static UUID parseUuid(String s) {
		String h = s.replace("-", "");
		return new UUID(Long.parseUnsignedLong(h.substring(0, 16), 16), Long.parseUnsignedLong(h.substring(16, 32), 16));
	}

	public static String compact(UUID id) {
		return id.toString().replace("-", "");
	}

	/** What offline-mode servers assign: {@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)}. */
	public static AltAccount offline(String name) {
		String uuid = compact(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
		return new AltAccount("offline-" + uuid, Kind.OFFLINE, name, uuid, null);
	}

	public static boolean validName(String name) {
		return name != null && name.length() >= 3 && name.length() <= 16 && name.chars().allMatch(c ->
				c < 128 && (Character.isLetterOrDigit(c) || c == '_'));
	}
}
