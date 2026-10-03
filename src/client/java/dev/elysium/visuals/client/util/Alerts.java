package dev.elysium.visuals.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Client-only warnings for modules: a UI sound and an action bar message. Nothing is sent to the server. */
public final class Alerts {
	private Alerts() {
	}

	/** Short note block "pling"; higher pitch sounds more urgent. */
	public static void ping(float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, pitch));
	}

	/** Message above the hotbar, in the given RGB color. */
	public static void actionBar(String text, int rgb) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(text).withColor(rgb & 0xFFFFFF));
		}
	}

	/** Message in the chat, visible only to this player. */
	public static void chat(Component message) {
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
	}
}
