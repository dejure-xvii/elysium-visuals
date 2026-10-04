package dev.elysium.visuals.client.compat;

import dev.elysium.visuals.ElysiumVisuals;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * Safe entry point to the optional Punchy! mod (first-person animations).
 * Never touches Punchy classes unless the mod is loaded; if the installed
 * Punchy version doesn't match what this was built against, the integration
 * switches itself off instead of crashing the game.
 */
public final class PunchyCompat {
	public static final String MOD_ID = "punchy";

	/** The Punchy settings Elysium shows in SwingAnimation. */
	public record Values(float animationSpeed, boolean customWalk, boolean sprintSwing, float sprintIntensity,
						 boolean itemPhysics, boolean particles) {
	}

	private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID);
	private static boolean broken;

	private PunchyCompat() {
	}

	/** Punchy is installed and compatible. */
	public static boolean available() {
		return INSTALLED && !broken;
	}

	/** Punchy is installed but this version can't be controlled. */
	public static boolean incompatible() {
		return INSTALLED && broken;
	}

	private static void fail(Throwable t) {
		if (!broken) {
			broken = true;
			ElysiumVisuals.LOGGER.warn("Punchy integration disabled: unsupported Punchy version", t);
		}
	}

	/** Whether Punchy currently animates the first-person hands. */
	public static boolean isEnabled() {
		if (!available()) {
			return false;
		}
		try {
			return PunchyBridge.isEnabled();
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	/**
	 * Turns Punchy's animations on or off for this session (Punchy's own config
	 * file is not changed). Switching off also clears state Punchy would otherwise
	 * leave behind (camera offset, suppressed use animations).
	 */
	public static void setEnabled(boolean on) {
		if (!available()) {
			return;
		}
		try {
			PunchyBridge.setEnabled(on);
		} catch (Throwable t) {
			fail(t);
		}
	}

	public static Values read() {
		if (!available()) {
			return null;
		}
		try {
			return PunchyBridge.read();
		} catch (Throwable t) {
			fail(t);
			return null;
		}
	}

	public static void apply(Values v) {
		if (!available()) {
			return;
		}
		try {
			PunchyBridge.apply(v);
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** Opens Punchy's own settings screen; false if that isn't possible. */
	public static boolean openSettings(Screen parent) {
		if (!available()) {
			return false;
		}
		try {
			PunchyBridge.openSettings(parent);
			return true;
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}
}
