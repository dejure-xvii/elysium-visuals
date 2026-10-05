package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.command.CommandManager;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Chat helpers: messages that mention your nickname are highlighted (with a
 * soft sound), and a command typed in the Russian layout is fixed before it is sent.
 */
public class ChatHelper extends Module {
	private final BooleanSetting mentions = add(new BooleanSetting("mentions", "Подсветка сообщений с вашим ником", true));
	private final ColorSetting mentionColor = add(new ColorSetting("mention_color", "Цвет подсветки", 0xFFFFD166)).visibleWhen(mentions::isOn);
	private final BooleanSetting mentionSound = add(new BooleanSetting("mention_sound", "Звук упоминания", true)).visibleWhen(mentions::isOn);
	private final BooleanSetting fixLayout = add(new BooleanSetting("fix_layout", "Исправлять русские команды", true));

	private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбю.ё";
	private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,./`";

	private long lastSound;

	public ChatHelper() {
		super("chat_helper", "ChatHelper", "Подсветка упоминаний ника и исправление команд в русской раскладке", Category.UTILS);
	}

	private static ChatHelper active() {
		ChatHelper m = ModuleManager.get().find(ChatHelper.class);
		return m != null && m.isEnabled() ? m : null;
	}

	// ---------------------------------------------------------------------
	// Russian layout
	// ---------------------------------------------------------------------

	/** The message to send instead of {@code message} (only the command word of "/…" and "….…" is converted). */
	public static String fixCommand(String message) {
		ChatHelper m = active();
		if (m == null || !m.fixLayout.isOn() || message.length() < 2) {
			return message;
		}
		char prefix = message.charAt(0);
		if (prefix != '/' && prefix != '.') {
			return message;
		}
		int end = message.indexOf(' ');
		String word = end < 0 ? message.substring(1) : message.substring(1, end);
		String rest = end < 0 ? "" : message.substring(end);
		if (!hasCyrillic(word)) {
			return message;
		}
		String converted = toQwerty(word);
		if (prefix == '.') {
			// In the Russian layout the "/" key types ".": a vanilla command unless it is one of ours.
			String name = converted.toLowerCase(Locale.ROOT);
			if (CommandManager.get().find(name) == null) {
				return "/" + converted + rest;
			}
		}
		return prefix + converted + rest;
	}

	static boolean hasCyrillic(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.UnicodeBlock.of(s.charAt(i)) == Character.UnicodeBlock.CYRILLIC) {
				return true;
			}
		}
		return false;
	}

	/** ЙЦУКЕН → QWERTY by key position, lower case (commands are lower case). */
	public static String toQwerty(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = Character.toLowerCase(s.charAt(i));
			int k = RU.indexOf(c);
			b.append(k >= 0 ? EN.charAt(k) : c);
		}
		return b.toString();
	}

	// ---------------------------------------------------------------------
	// Mentions
	// ---------------------------------------------------------------------

	/** The highlighted message if it mentions you, or null to leave it as is. */
	public static Component highlight(Component message) {
		ChatHelper m = active();
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.mentions.isOn() || mc.player == null) {
			return null;
		}
		String nick = mc.getUser().getName();
		String plain = message.getString();
		List<int[]> hits = mentions(plain, nick);
		if (hits.isEmpty()) {
			return null;
		}
		int color = m.mentionColor.argb() & 0xFFFFFF;
		MutableComponent out = Component.empty();
		int[] offset = {0};
		message.visit((style, text) -> {
			int start = offset[0], end = start + text.length();
			int pos = start;
			for (int[] h : hits) {
				int hs = Math.max(h[0], start), he = Math.min(h[1], end);
				if (hs >= he) {
					continue;
				}
				if (hs > pos) {
					out.append(Component.literal(plain.substring(pos, hs)).withStyle(style.withColor(color)));
				}
				out.append(Component.literal(plain.substring(hs, he)).withStyle(style.withColor(color).withBold(true).withUnderlined(true)));
				pos = he;
			}
			if (pos < end) {
				out.append(Component.literal(plain.substring(pos, end)).withStyle(style.withColor(color)));
			}
			offset[0] = end;
			return Optional.empty();
		}, Style.EMPTY);
		if (m.mentionSound.isOn() && Util.getMillis() - m.lastSound > 700) {
			m.lastSound = Util.getMillis();
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.35f));
		}
		return out;
	}

	public static int mentionColor() {
		ChatHelper m = active();
		return m == null ? -1 : m.mentionColor.argb() | 0xFF000000;
	}

	/**
	 * Where the nickname appears as a word, leaving out the sender part of
	 * your own messages ("<Nick> …", "Nick: …", "Nick » …").
	 */
	static List<int[]> mentions(String text, String nick) {
		List<int[]> list = new ArrayList<>();
		if (nick == null || nick.length() < 3) {
			return list;
		}
		String lower = text.toLowerCase(Locale.ROOT), n = nick.toLowerCase(Locale.ROOT);
		for (int i = lower.indexOf(n); i >= 0; i = lower.indexOf(n, i + 1)) {
			int e = i + n.length();
			boolean wordStart = i == 0 || !isNickChar(lower.charAt(i - 1));
			boolean wordEnd = e >= lower.length() || !isNickChar(lower.charAt(e));
			if (!wordStart || !wordEnd) {
				continue;
			}
			int j = e;
			while (j < lower.length() && (lower.charAt(j) == ']' || lower.charAt(j) == ')' || lower.charAt(j) == ' ')) {
				j++;
			}
			char next = j < lower.length() ? lower.charAt(j) : ' ';
			if (next == '>' || next == ':' || next == '»' || next == '→' || next == '|') {
				continue; // you are the sender
			}
			list.add(new int[]{i, e});
		}
		return list;
	}

	private static boolean isNickChar(char c) {
		return c < 128 && (Character.isLetterOrDigit(c) || c == '_');
	}
}
