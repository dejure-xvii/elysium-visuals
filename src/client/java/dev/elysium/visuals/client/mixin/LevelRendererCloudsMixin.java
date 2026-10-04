package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.render.CloudRenderer3D;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla (or Sodium) cloud pass while CustomSky's 3D clouds are drawn.
 * Sodium only changes the insides of the cloud renderer, so skipping the pass
 * removes both.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererCloudsMixin {
	@Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
	private void elysium$no3dDoubleClouds(CallbackInfo ci) {
		if (CloudRenderer3D.active()) {
			ci.cancel();
		}
	}
}
