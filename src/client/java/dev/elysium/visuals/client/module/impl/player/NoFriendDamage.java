package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.impl.utils.FriendsModule;
import dev.elysium.visuals.client.notify.Notifications;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/** Cancels hits on players from your friend list: the attack simply doesn't happen. */
public class NoFriendDamage extends Module {
	private long lastNoticeMs;

	public NoFriendDamage() {
		super("no_friend_damage", "NoFriendDamage", "Не даёт случайно ударить друга", Category.PLAYER);
		// Registered before the other attack listeners, so a cancelled hit isn't tracked as a target either.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (isEnabled() && level.isClientSide() && entity instanceof Player target
					&& FriendsModule.isFriend(target.getGameProfile().name())) {
				long now = Util.getMillis();
				if (now - lastNoticeMs > 2000) {
					lastNoticeMs = now;
					Notifications.module("NoFriendDamage", "Удар по " + target.getGameProfile().name() + " отменён");
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}
}
