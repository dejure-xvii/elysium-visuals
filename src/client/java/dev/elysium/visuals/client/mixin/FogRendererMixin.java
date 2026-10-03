package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ambience "normal" fog: custom start/end distances. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void elysium$fog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darken, ClientLevel level,
							 CallbackInfoReturnable<FogData> cir) {
		Ambience.modifyFog(cir.getReturnValue());
	}
}
