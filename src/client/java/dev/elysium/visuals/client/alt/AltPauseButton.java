package dev.elysium.visuals.client.alt;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/** "Сменить аккаунт" in the pause menu; only active while not connected to a server. */
public final class AltPauseButton {
	private AltPauseButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof PauseScreen)) {
				return;
			}
			Button button = Button.builder(Component.literal("Сменить аккаунт"), b -> mc.gui.setScreen(new AltManagerScreen(screen)))
					.bounds(6, 6, 110, 20)
					.build();
			if (!AltManager.canSwitch()) {
				button.active = false;
				button.setTooltip(Tooltip.create(Component.literal("Выйдите с сервера, чтобы сменить аккаунт")));
			}
			Screens.getWidgets(screen).add(button);
		});
	}
}
