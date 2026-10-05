package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.utils.LockSlot;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LockSlot: Q does nothing on a locked hotbar slot. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerDropMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void elysium$lockSlot(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
		if (LockSlot.blocksDrop((LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
