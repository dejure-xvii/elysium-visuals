package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Optimizer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optimizer "Убрать траву": grass and flowers report no model, so chunk meshing (vanilla or Sodium) skips them. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockRenderShapeMixin {
	@Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
	private void elysium$hideGrass(CallbackInfoReturnable<RenderShape> cir) {
		if (Optimizer.hidesBlock((BlockState) (Object) this)) {
			cir.setReturnValue(RenderShape.INVISIBLE);
		}
	}
}
