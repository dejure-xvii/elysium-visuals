package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.FullBright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FullBright: brightness / night vision in the lightmap only. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
	@Inject(method = "extract", at = @At("RETURN"))
	private void elysium$fullBright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.needsUpdate) {
			FullBright.apply(state);
		}
	}
}
