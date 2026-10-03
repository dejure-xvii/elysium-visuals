package dev.elysium.visuals.client.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * One quad drawn with {@link GuiPipelines#ROUNDED_RECT}. Shape parameters are
 * packed into the UV attributes (see rounded_rect.vsh).
 *
 * @param x0 quad bounds (may be larger than the shape, e.g. for glows)
 * @param cx shape center
 * @param colorTL corner colors; vary along one axis only for exact gradients
 */
public record ShapeRenderState(
		Matrix3x2fc pose,
		float x0, float y0, float x1, float y1,
		float cx, float cy,
		int halfW16, int halfH16, int radius16, int mode16,
		int colorTL, int colorBL, int colorBR, int colorTR,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vc) {
		vertex(vc, x0, y0, colorTL);
		vertex(vc, x0, y1, colorBL);
		vertex(vc, x1, y1, colorBR);
		vertex(vc, x1, y0, colorTR);
	}

	private void vertex(VertexConsumer vc, float x, float y, int color) {
		vc.addVertexWith2DPose(pose, x, y)
				.setColor(color)
				.setUv(x - cx, y - cy)
				.setUv1(halfW16, halfH16)
				.setUv2(radius16, mode16)
				.setNormal(0f, 0f, 1f);
	}

	@Override
	public RenderPipeline pipeline() {
		return GuiPipelines.ROUNDED_RECT;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}
}
