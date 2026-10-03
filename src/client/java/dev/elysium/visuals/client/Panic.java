package dev.elysium.visuals.client;

import dev.elysium.visuals.client.gui.ClickGuiScreen;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;

/**
 * {@code .panic}: hides the whole client until the game restarts. Modules are
 * switched off, the HUD and the ClickGUI key stop working, chat commands are no
 * longer intercepted, and nothing is written to the config, so the next launch
 * starts with the settings exactly as they were.
 */
public final class Panic {
	private static boolean active;

	private Panic() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void activate() {
		// Set first: from here on the config can't be saved, so the disabled state below never reaches disk.
		active = true;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof ClickGuiScreen) {
			mc.gui.setScreen(null);
		}
		ModuleManager.get().silently(() -> {
			for (Module m : ModuleManager.get().modules()) {
				m.setEnabled(false);
			}
		});
	}
}
