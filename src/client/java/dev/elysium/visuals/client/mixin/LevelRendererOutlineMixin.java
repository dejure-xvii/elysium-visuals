package dev.elysium.visuals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.elysium.visuals.client.module.impl.player.PvPHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PvPHelper outlines stop at walls: right after the entity outline target is
 * cleared (the opaque terrain is drawn by then), the world's depth is copied
 * into it, so the hidden parts of an outlined entity fail the depth test.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
	@WrapOperation(method = "lambda$addMainPass$0", require = 0, at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void elysium$outlineDepth(CommandEncoder encoder, GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth,
									  Operation<Void> original) {
		original.call(encoder, color, clearColor, depth, clearDepth);
		PvPHelper.outlineHookRan = true;
		if (!PvPHelper.outlinesActive() || depth == null) {
			return;
		}
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTexture worldDepth = main.getDepthTexture();
		if (worldDepth != null && worldDepth.getWidth(0) == depth.getWidth(0) && worldDepth.getHeight(0) == depth.getHeight(0)
				&& worldDepth.getFormat() == depth.getFormat()) {
			encoder.copyTextureToTexture(worldDepth, depth, 0, 0, 0, 0, 0, depth.getWidth(0), depth.getHeight(0));
		}
	}
}
