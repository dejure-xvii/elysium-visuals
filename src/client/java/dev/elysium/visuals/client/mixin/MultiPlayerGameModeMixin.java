package dev.elysium.visuals.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.elysium.visuals.client.module.impl.utils.FakePlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FakePlayer: an attack on the client-only copy is never sent to the server
 * (it doesn't know that entity); the hit is simulated locally instead. The rest
 * of the attack (swing, our attack listeners) runs as usual.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@WrapWithCondition(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private boolean elysium$fakeAttack(ClientPacketListener connection, Packet<?> packet, Player player, Entity entity) {
		if (FakePlayer.isFake(entity)) {
			FakePlayer.onAttacked(player, entity);
			return false;
		}
		return true;
	}
}
