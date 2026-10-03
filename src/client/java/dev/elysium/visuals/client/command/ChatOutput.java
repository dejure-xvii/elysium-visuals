package dev.elysium.visuals.client.command;

import dev.elysium.visuals.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-only chat replies: "[Elysium]" in the theme accent, then the message. Never sent to the server. */
public final class ChatOutput {
	private static final int TEXT = 0xFFFFFF;
	private static final int ERROR = 0xFF5555;
	private static final int DIM = 0xAAAAAA;

	private ChatOutput() {
	}

	public static void info(String message) {
		send(Component.literal(message).withColor(TEXT));
	}

	public static void error(String message) {
		send(Component.literal(message).withColor(ERROR));
	}

	/** A highlighted value inside an info line, e.g. a name. */
	public static MutableComponent accent(String text) {
		return Component.literal(text).withColor(accentRgb());
	}

	public static MutableComponent dim(String text) {
		return Component.literal(text).withColor(DIM);
	}

	public static MutableComponent text(String text) {
		return Component.literal(text).withColor(TEXT);
	}

	public static void send(Component message) {
		Minecraft mc = Minecraft.getInstance();
		MutableComponent line = Component.literal("[Elysium] ").withColor(accentRgb()).append(message);
		mc.gui.hud.getChat().addClientSystemMessage(line);
	}

	private static int accentRgb() {
		return ThemeManager.get().palette().accent() & 0xFFFFFF;
	}
}
