package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Friend list. Other modules ask {@link #isFriend(String)}; switching the
 * module off disables the friend system without losing the list.
 */
public class FriendsModule extends Module {
	// Global: the friend list is shared by all configs, .cfg load/reset don't touch it.
	private final StringListSetting friends = add(new StringListSetting("names", "Друзья", "Ник игрока…",
			name -> name.matches("[A-Za-z0-9_]{1,16}"))).global();

	public FriendsModule() {
		super("friends", "Friends", "Список друзей для HUD и других модулей", Category.UTILS);
		enableByDefault();
	}

	private static FriendsModule instance() {
		FriendsModule m = ModuleManager.get().find(FriendsModule.class);
		return m != null && m.isEnabled() ? m : null;
	}

	public static boolean isFriend(String name) {
		FriendsModule m = instance();
		return m != null && m.friends.contains(name);
	}

	/**
	 * The friend list itself, editable even while the module is off (used by
	 * {@code .friend}). Null only before modules are registered.
	 */
	public static StringListSetting list() {
		FriendsModule m = ModuleManager.get().find(FriendsModule.class);
		return m == null ? null : m.friends;
	}

	public static int friendCount() {
		FriendsModule m = instance();
		return m == null ? 0 : m.friends.get().size();
	}

	/** Friends present in the current server's player list, in list order. */
	public static List<String> onlineFriends() {
		Minecraft mc = Minecraft.getInstance();
		FriendsModule m = instance();
		List<String> online = new ArrayList<>();
		if (m == null || mc.getConnection() == null || m.friends.get().isEmpty()) {
			return online;
		}
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (m.friends.contains(name) && (mc.player == null || !mc.player.getUUID().equals(info.getProfile().id()))) {
				online.add(name);
			}
		}
		return online;
	}
}
