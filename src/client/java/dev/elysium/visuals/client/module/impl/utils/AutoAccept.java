package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.notify.Notifications;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accepts teleport requests automatically: when a "X wants to teleport to you"
 * message arrives (Russian or English plugin wording), sends {@code /tpaccept}.
 */
public class AutoAccept extends Module {
	/** Group 1 is the requesting player's nick. */
	private static final List<Pattern> REQUESTS = List.of(
			Pattern.compile("([A-Za-z0-9_]{3,16}) (?:просит|хочет) (?:к вам |к тебе )?телепортироваться"),
			Pattern.compile("([A-Za-z0-9_]{3,16}) (?:просит|хочет), чтобы (?:вы|ты) телепортировал"),
			Pattern.compile("([A-Za-z0-9_]{3,16}) (?:отправил|прислал)[а]? (?:вам |тебе )?запрос на телепорт"),
			Pattern.compile("[Зз]апрос на телепорт(?:ацию)? от (?:игрока )?([A-Za-z0-9_]{3,16})"),
			Pattern.compile("([A-Za-z0-9_]{3,16}) has requested (?:to teleport to you|that you teleport to them)"),
			Pattern.compile("([A-Za-z0-9_]{3,16}) (?:wants|would like) to teleport to you"),
			Pattern.compile("[Tt]eleport request from ([A-Za-z0-9_]{3,16})"),
			Pattern.compile("([A-Za-z0-9_]{3,16}) sent you a teleport request"));
	private static final long COOLDOWN_MS = 1500;

	private final BooleanSetting friendsOnly = add(new BooleanSetting("friends_only", "Только от друзей", false));

	private long lastAcceptMs;

	public AutoAccept() {
		super("auto_accept", "AutoAccept", "Автоматически принимает запросы на телепорт (/tpaccept)", Category.UTILS);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message);
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> onMessage(message));
	}

	private void onMessage(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null || mc.getConnection() == null) {
			return;
		}
		String text = message.getString();
		for (Pattern pattern : REQUESTS) {
			Matcher m = pattern.matcher(text);
			if (!m.find()) {
				continue;
			}
			String from = m.group(1);
			if (from.equalsIgnoreCase(mc.getUser().getName())) {
				return; // our own request echoed back
			}
			if (friendsOnly.isOn() && !FriendsModule.isFriend(from)) {
				return;
			}
			long now = Util.getMillis();
			if (now - lastAcceptMs < COOLDOWN_MS) {
				return;
			}
			lastAcceptMs = now;
			mc.getConnection().sendCommand("tpaccept");
			Notifications.module("AutoAccept", "Принят запрос на телепорт от " + from);
			return;
		}
	}

	/** For tests: the nick in a teleport request message, or null if it isn't one. */
	public static String requester(String text) {
		for (Pattern pattern : REQUESTS) {
			Matcher m = pattern.matcher(text);
			if (m.find()) {
				return m.group(1).toLowerCase(Locale.ROOT);
			}
		}
		return null;
	}
}
