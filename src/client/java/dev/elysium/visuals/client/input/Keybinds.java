package dev.elysium.visuals.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key bindings; they appear in Options → Controls → Key Binds under "Elysium Visuals". */
public final class Keybinds {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ElysiumVisuals.id("main"));

	public static final KeyMapping OPEN_GUI = new KeyMapping(
			"key.elysium-visuals.open_gui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			CATEGORY);

	private Keybinds() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(OPEN_GUI);

		// Opening is handled here; closing is handled by ClickGuiScreen itself,
		// because key mappings don't fire while a screen is open.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_GUI.consumeClick()) {
				if (client.gui.screen() == null && !Panic.isActive()) {
					client.gui.setScreen(new ClickGuiScreen());
				}
			}
		});
	}
}
