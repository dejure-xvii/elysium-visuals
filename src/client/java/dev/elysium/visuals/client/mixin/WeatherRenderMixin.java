package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Optimizer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optimizer "Убрать погоду": no rain or snow drawn. */
@Mixin(WeatherEffectRenderer.class)
abstract class WeatherRenderMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void elysium$noWeather(Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
		if (Optimizer.active("weather")) {
			ci.cancel();
		}
	}
}
