package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import dev.elysium.visuals.client.module.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

/**
 * Replaces your nickname (and optionally your friends') in every piece of text
 * the game draws: chat, tab list, name tags, scoreboard, our HUD. Works at the
 * lowest text level, so nothing is missed; only the display changes.
 */
public class NameProtect extends Module {
	/** Style insertion that marks text as exempt (drawn as-is). */
	public static final String RAW = "elysium:raw";
	private static final String FRIEND_NAME = "Друг";

	private final StringSetting name = add(new StringSetting("name", "Своё имя", "Elysium", 32));
	private final BooleanSetting watermark = add(new BooleanSetting("watermark", "Скрывать в ватермарке", true));
	private final BooleanSetting friends = add(new BooleanSetting("friends", "Скрывать ники друзей", false));

	private static NameProtect instance;

	public NameProtect() {
		super("name_protect", "NameProtect", "Заменяет твой ник на свой текст везде на экране", Category.UTILS);
		instance = this;
	}

	/** Whether the watermark should show the protected name (else it shows the real one, marked raw). */
	public static boolean hidesInWatermark() {
		return instance != null && instance.isEnabled() && instance.watermark.isOn();
	}

	/** Called (mixin) for every string about to be drawn or measured; returns it unchanged or with names replaced. */
	public static String protect(String text, Style style) {
		NameProtect m = instance;
		if (m == null || !m.isEnabled() || text.length() < 3 || RAW.equals(style.getInsertion())) {
			return text;
		}
		Minecraft mc = Minecraft.getInstance();
		String own = mc.getUser().getName();
		String result = text;
		if (own != null && own.length() >= 3 && result.contains(own)) {
			result = result.replace(own, m.name.get());
		}
		if (m.friends.isOn()) {
			StringListSetting list = FriendsModule.list();
			if (list != null) {
				for (String friend : list.get()) {
					if (result.contains(friend) && !friend.equals(own)) {
						result = result.replace(friend, FRIEND_NAME);
					}
				}
			}
		}
		return result;
	}
}
