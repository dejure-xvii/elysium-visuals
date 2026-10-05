package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Optimizer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optimizer "Убрать партиклы": particles are never added, so they cost nothing to tick or draw. */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineAddMixin {
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void elysium$noParticles(Particle particle, CallbackInfo ci) {
		if (Optimizer.active("particles")) {
			ci.cancel();
		}
	}
}
