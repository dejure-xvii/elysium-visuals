package dev.elysium.visuals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.elysium.visuals.client.module.impl.player.PvPHelper;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PvPHelper: highlighted players get their group's outline color. */
@Mixin(EntityRenderer.class)
abstract class EntityOutlineMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int elysium$outlineColor(Entity entity, Operation<Integer> original) {
		int c = PvPHelper.outlineColor(entity);
		return c != 0 ? c & 0xFFFFFF : original.call(entity);
	}
}
