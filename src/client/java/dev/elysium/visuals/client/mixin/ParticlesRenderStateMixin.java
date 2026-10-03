package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.NoRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender particles: vanilla particles aren't drawn (our Particles module renders separately). */
@Mixin(ParticlesRenderState.class)
public abstract class ParticlesRenderStateMixin {
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void elysium$noParticles(SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (NoRender.hides("particles")) {
			ci.cancel();
		}
	}
}
