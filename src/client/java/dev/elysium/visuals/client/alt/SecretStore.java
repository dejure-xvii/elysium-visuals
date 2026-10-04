package dev.elysium.visuals.client.alt;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.util.Util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Microsoft refresh tokens, kept in the Windows Credential Manager (encrypted
 * by Windows for the current user) under the same name Elysium Launcher uses
 * (the keyring crate's {@code "<user>.<service>"} = {@code "msa:<id>.ElysiumLauncher"}),
 * so a sign-in made in either place works in both.
 *
 * <p>Tokens are never written to the mod's files or the log. Where the
 * Credential Manager isn't available they are only kept in memory for this session.
 */
public final class SecretStore {
	private static final String SERVICE = "ElysiumLauncher";
	private static final int CRED_TYPE_GENERIC = 1;
	private static final int CRED_PERSIST_ENTERPRISE = 3;

	private static final Map<String, String> MEMORY = new ConcurrentHashMap<>();
	private static Boolean available;

	private SecretStore() {
	}

	/** Whether tokens survive a restart (Windows Credential Manager reachable). */
	public static synchronized boolean persistent() {
		if (available == null) {
			available = false;
			if (Util.getPlatform() == Util.OS.WINDOWS) {
				try {
					Advapi.get();
					available = true;
				} catch (Throwable t) {
					ElysiumVisuals.LOGGER.warn("Windows Credential Manager unavailable: {}", t.getClass().getSimpleName());
				}
			}
		}
		return available;
	}

	private static String user(String accountId) {
		return "msa:" + accountId;
	}

	private static String target(String accountId) {
		return user(accountId) + "." + SERVICE;
	}

	public static String get(String accountId) {
		if (!persistent()) {
			return MEMORY.get(accountId);
		}
		PointerByReference ref = new PointerByReference();
		if (!Advapi.get().CredReadW(new WString(target(accountId)), CRED_TYPE_GENERIC, 0, ref)) {
			return null;
		}
		try {
			Credential c = new Credential(ref.getValue());
			if (c.CredentialBlob == null || c.CredentialBlobSize <= 0) {
				return null;
			}
			return new String(c.CredentialBlob.getByteArray(0, c.CredentialBlobSize), StandardCharsets.UTF_8);
		} finally {
			Advapi.get().CredFree(ref.getValue());
		}
	}

	public static void put(String accountId, String token) {
		if (!persistent()) {
			MEMORY.put(accountId, token);
			return;
		}
		byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
		Memory blob = new Memory(bytes.length);
		blob.write(0, bytes, 0, bytes.length);
		Credential c = new Credential();
		c.Type = CRED_TYPE_GENERIC;
		c.TargetName = new WString(target(accountId));
		c.Comment = new WString("Elysium Microsoft sign-in");
		c.CredentialBlobSize = bytes.length;
		c.CredentialBlob = blob;
		c.Persist = CRED_PERSIST_ENTERPRISE;
		c.UserName = new WString(user(accountId));
		c.write();
		try {
			if (!Advapi.get().CredWriteW(c, 0)) {
				ElysiumVisuals.LOGGER.warn("Can't store the sign-in in the Credential Manager (error {})", Native.getLastError());
				MEMORY.put(accountId, token);
			}
		} finally {
			blob.clear();
		}
	}

	public static void delete(String accountId) {
		MEMORY.remove(accountId);
		if (persistent()) {
			Advapi.get().CredDeleteW(new WString(target(accountId)), CRED_TYPE_GENERIC, 0);
		}
	}

	// ---------------------------------------------------------------------
	// advapi32 (JNA)
	// ---------------------------------------------------------------------

	interface Advapi extends Library {
		static Advapi get() {
			return Holder.INSTANCE;
		}

		boolean CredReadW(WString target, int type, int flags, PointerByReference credential);

		boolean CredWriteW(Credential credential, int flags);

		boolean CredDeleteW(WString target, int type, int flags);

		void CredFree(Pointer buffer);
	}

	private static final class Holder {
		static final Advapi INSTANCE = Native.load("Advapi32", Advapi.class);
	}

	/** CREDENTIALW */
	@Structure.FieldOrder({"Flags", "Type", "TargetName", "Comment", "LastWrittenLow", "LastWrittenHigh", "CredentialBlobSize",
			"CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName"})
	public static final class Credential extends Structure {
		public int Flags;
		public int Type;
		public WString TargetName;
		public WString Comment;
		public int LastWrittenLow;
		public int LastWrittenHigh;
		public int CredentialBlobSize;
		public Pointer CredentialBlob;
		public int Persist;
		public int AttributeCount;
		public Pointer Attributes;
		public WString TargetAlias;
		public WString UserName;

		public Credential() {
		}

		Credential(Pointer p) {
			super(p);
			read();
		}
	}
}
