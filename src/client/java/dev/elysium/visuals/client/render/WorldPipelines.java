package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Render types for in-world effects. All of them blend additively (glow),
 * never write depth and don't cull faces; each comes in a depth-tested and a
 * see-through ("xray") variant. Everything lives in immutable pipelines, so no
 * GPU state is left behind after drawing.
 */
public final class WorldPipelines {
	/** Untextured glowing geometry: lines, ribbons, shapes ({@code POSITION_COLOR}). */
	public static final RenderType GLOW = color("glow_color", false, BlendFunction.ADDITIVE);
	public static final RenderType GLOW_XRAY = color("glow_color_xray", true, BlendFunction.ADDITIVE);
	/**
	 * Same geometry blended normally (premultiplied alpha) instead of added:
	 * keeps the theme hue on bright backgrounds (lines against a daytime sky).
	 */
	public static final RenderType SOLID = color("solid_color", false, BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
	public static final RenderType SOLID_XRAY = color("solid_color_xray", true, BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
	/** Plasma fill ({@code POSITION_TEX_COLOR_NORMAL}: plasma coords, color A, color B in the normal). */
	public static final RenderType PLASMA = plasma("plasma", false);
	public static final RenderType PLASMA_XRAY = plasma("plasma_xray", true);

	private WorldPipelines() {
	}

	private static RenderPipeline pipeline(String name, String shader, VertexFormat format, boolean xray, BlendFunction blend) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(ElysiumVisuals.id("pipeline/" + name))
				.withVertexShader(ElysiumVisuals.id("core/" + shader))
				.withFragmentShader(ElysiumVisuals.id("core/" + shader))
				.withVertexBinding(0, format)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withColorTargetState(new ColorTargetState(blend))
				.withCull(false)
				.withDepthStencilState(new DepthStencilState(xray ? CompareOp.ALWAYS_PASS : DepthStencilState.DEFAULT.depthTest(), false))
				.build());
	}

	private static RenderType color(String name, boolean xray, BlendFunction blend) {
		return RenderType.create(ElysiumVisuals.MOD_ID + "_" + name,
				RenderSetup.builder(pipeline(name, "glow_color", DefaultVertexFormat.POSITION_COLOR, xray, blend)).createRenderSetup());
	}

	private static RenderType plasma(String name, boolean xray) {
		return RenderType.create(ElysiumVisuals.MOD_ID + "_" + name,
				RenderSetup.builder(pipeline(name, "plasma", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, xray, BlendFunction.ADDITIVE))
						.createRenderSetup());
	}

	public static RenderType glow(boolean xray) {
		return xray ? GLOW_XRAY : GLOW;
	}

	public static RenderType solid(boolean xray) {
		return xray ? SOLID_XRAY : SOLID;
	}

	public static RenderType plasma(boolean xray) {
		return xray ? PLASMA_XRAY : PLASMA;
	}

	/** Forces class initialisation so the pipelines are registered before resources load. */
	public static void init() {
	}
}
