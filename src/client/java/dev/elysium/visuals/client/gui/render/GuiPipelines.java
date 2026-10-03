package dev.elysium.visuals.client.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.renderer.RenderPipelines;

/** Custom GPU pipelines used by the ClickGUI. */
public final class GuiPipelines {
	/**
	 * Anti-aliased rounded rectangles, outlines and glows computed in the
	 * fragment shader ({@code shaders/core/rounded_rect.*}): one quad per shape.
	 */
	public static final RenderPipeline ROUNDED_RECT = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
					.withLocation(ElysiumVisuals.id("pipeline/rounded_rect"))
					.withVertexShader(ElysiumVisuals.id("core/rounded_rect"))
					.withFragmentShader(ElysiumVisuals.id("core/rounded_rect"))
					.withVertexBinding(0, DefaultVertexFormat.ENTITY)
					.build());

	private GuiPipelines() {
	}

	/** Forces class initialisation so the pipeline is registered before resources load. */
	public static void init() {
	}
}
