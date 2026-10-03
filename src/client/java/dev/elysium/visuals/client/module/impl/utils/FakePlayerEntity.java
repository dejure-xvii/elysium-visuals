package dev.elysium.visuals.client.module.impl.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;

import java.util.UUID;

/**
 * The client-only copy of the local player. It needs its own UUID (the client
 * indexes entities by UUID), so the skin is borrowed from the local player's
 * tab-list entry.
 */
public class FakePlayerEntity extends RemotePlayer {
	public FakePlayerEntity(ClientLevel level, String name) {
		super(level, new GameProfile(UUID.randomUUID(), name));
	}

	@Override
	protected PlayerInfo getPlayerInfo() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && mc.player != null) {
			PlayerInfo own = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (own != null) {
				return own;
			}
		}
		return super.getPlayerInfo();
	}
}
