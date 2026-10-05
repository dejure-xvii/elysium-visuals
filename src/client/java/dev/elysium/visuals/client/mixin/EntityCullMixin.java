package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Optimizer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optimizer entity options: behind walls, far and tiny, beyond the distance limit. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityCullMixin {
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private <E extends Entity> void elysium$cull(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Optimizer.cullEntity(entity)) {
			cir.setReturnValue(false);
		}
	}
}
