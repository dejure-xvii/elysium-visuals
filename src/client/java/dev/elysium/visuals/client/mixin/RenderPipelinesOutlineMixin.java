package dev.elysium.visuals.client.mixin;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * PvPHelper outlines stop at walls: the entity outline pipelines get the
 * usual depth test. Vanilla clears the outline depth to "far" every frame, so
 * on its own nothing changes; only when PvPHelper copies the world's depth in
 * (see {@code LevelRendererOutlineMixin}) do walls hide the outline.
 */
@Mixin(RenderPipelines.class)
abstract class RenderPipelinesOutlineMixin {
	@ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/RenderPipelines;register(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
	private static RenderPipeline elysium$outlineDepth(RenderPipeline pipeline) {
		String path = pipeline.getLocation().getPath();
		if (!path.equals("pipeline/outline_cull") && !path.equals("pipeline/outline_no_cull")) {
			return pipeline;
		}
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
				.withLocation(pipeline.getLocation())
				.withDepthStencilState(DepthStencilState.DEFAULT);
		if (path.endsWith("no_cull")) {
			b.withCull(false);
		}
		return b.build();
	}
}
