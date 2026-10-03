package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Particles;
import dev.elysium.visuals.client.module.impl.utils.UseTracker;
import dev.elysium.visuals.client.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totem pops (entity event 35, handled right here rather than in the entity)
 * and item pickups, both on the main thread: we inject after the thread check,
 * because the packet first arrives on the network thread and is only re-queued
 * there, so HEAD would fire twice.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final String THREAD_CHECK =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = THREAD_CHECK, shift = At.Shift.AFTER))
	private void elysium$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH || mc.level == null) {
			return;
		}
		Entity entity = packet.getEntity(mc.level);
		if (entity instanceof LivingEntity living) {
			Particles.onTotem(living);
			UseTracker.onTotem(living);
		}
	}

	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target = THREAD_CHECK, shift = At.Shift.AFTER))
	private void elysium$onPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || packet.getPlayerId() != mc.player.getId()) {
			return;
		}
		if (mc.level.getEntity(packet.getItemId()) instanceof ItemEntity item) {
			Notifications.pickup(item.getItem().copy(), packet.getAmount());
		}
	}
}
