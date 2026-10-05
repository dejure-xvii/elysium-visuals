package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.player.PvPHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PvPHelper: highlighted players (you, friends, the target) get an outline. */
@Mixin(Minecraft.class)
abstract class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void elysium$pvpOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && PvPHelper.outlineColor(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
