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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FakePlayer: an attack on the client-only copy is never sent to the server
 * (it doesn't know that entity); the hit is simulated locally instead. The rest
 * of the attack (swing, our attack listeners) runs as usual.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	/** CustomCrystal: no hit on a crystal whose blast would hurt you too much. */
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void elysium$crystalGuard(Player player, Entity entity, CallbackInfo ci) {
		if (dev.elysium.visuals.client.module.impl.render.CustomCrystal.blocksAttack(entity)) {
			ci.cancel();
		}
	}

	/** CrystalOptimizer: the hit crystal disappears on the client at once. */
	@Inject(method = "attack", at = @At("TAIL"))
	private void elysium$crystalOptimizer(Player player, Entity entity, CallbackInfo ci) {
		dev.elysium.visuals.client.module.impl.utils.CrystalOptimizer.onAttacked(entity);
	}

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
