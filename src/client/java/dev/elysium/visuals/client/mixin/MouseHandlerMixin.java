package dev.elysium.visuals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.elysium.visuals.client.module.impl.utils.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: slower mouse look while zoomed, and the wheel changes the zoom instead of the slot. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void elysium$zoomSensitivity(LocalPlayer player, double xo, double yo, Operation<Void> original) {
		double k = Zoom.sensitivity();
		original.call(player, xo * k, yo * k);
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void elysium$zoomWheel(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (handle == minecraft.getWindow().handle() && minecraft.gui.screen() == null && minecraft.gui.overlay() == null
				&& minecraft.player != null && Zoom.onScroll(yoffset)) {
			ci.cancel();
		}
	}
}
