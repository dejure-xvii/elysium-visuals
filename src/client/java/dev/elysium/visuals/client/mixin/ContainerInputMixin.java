package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.utils.LockSlot;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LockSlot: clicks that would throw a locked item out of the window are dropped. */
@Mixin(MultiPlayerGameMode.class)
abstract class ContainerInputMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void elysium$lockSlot(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (player.containerMenu.containerId == containerId && LockSlot.blocksClick(player.containerMenu, slotId, button, input)) {
			ci.cancel();
		}
	}
}
