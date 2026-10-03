package dev.elysium.visuals.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings; they appear in Options → Controls → Key Binds
 * under "Elysium Visuals".
 */
public final class Keybinds {

	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(ElysiumVisuals.id("main"));

	// Открытие ClickGUI — Right Shift
	public static final KeyMapping OPEN_GUI = new KeyMapping(
			"key.elysium-visuals.open_gui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			CATEGORY
	);

	// ClickPearl — V
	public static final KeyMapping CLICK_PEARL = new KeyMapping(
			"key.elysium-visuals.click_pearl",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			CATEGORY
	);

	private Keybinds() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(OPEN_GUI);
		KeyMappingHelper.registerKeyMapping(CLICK_PEARL);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {

			// Открытие GUI
			while (OPEN_GUI.consumeClick()) {
				if (client.gui.screen() == null && !Panic.isActive()) {
					client.gui.setScreen(new ClickGuiScreen());
				}
			}

		});
	}
}