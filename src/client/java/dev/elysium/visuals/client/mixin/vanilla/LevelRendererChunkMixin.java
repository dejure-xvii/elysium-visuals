package dev.elysium.visuals.client.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elysium.visuals.client.module.impl.render.ChunkAnimator;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ChunkAnimator (vanilla renderer): each section's model-view matrix is
 * offset while it slides in. Optional, since Sodium replaces this renderer.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererChunkMixin {
	@WrapOperation(method = "prepareChunkRenders", require = 0, at = @At(value = "NEW",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms$ChunkSectionInfo;<init>(Lorg/joml/Matrix4fc;IIIFII)V"))
	private DynamicUniforms.ChunkSectionInfo elysium$animate(Matrix4fc modelView, int x, int y, int z, float visibility,
															 int atlasWidth, int atlasHeight, Operation<DynamicUniforms.ChunkSectionInfo> original,
															 @Local SectionRenderDispatcher.RenderSection section) {
		Matrix4fc animated = ChunkAnimator.animate(modelView, section.getRenderOrigin(),
				((RenderSectionAccessor) section).elysium$uploadedTime());
		return original.call(animated, x, y, z, visibility, atlasWidth, atlasHeight);
	}
}
