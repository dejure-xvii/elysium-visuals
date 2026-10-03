package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.SwingAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SwingAnimation speed: only the local player's own swing animation length changes. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
	private void elysium$swingSpeed(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this == Minecraft.getInstance().player) {
			cir.setReturnValue(SwingAnimation.swingDuration(cir.getReturnValue()));
		}
	}
}
