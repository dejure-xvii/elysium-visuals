package dev.elysium.visuals.client.particle;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.EnumMap;
import java.util.Map;

/**
 * GPU state for the world particles. Everything the draw needs (additive
 * blending, no depth writes, no face culling, depth test on/off) is part of
 * the immutable pipeline, so the game binds it only for our draw calls and
 * nothing leaks into the rest of the frame.
 */
public final class ParticleRenderTypes {
	private static RenderPipeline pipeline(String name, CompareOp depthTest) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(ElysiumVisuals.id("pipeline/" + name))
				.withVertexShader(ElysiumVisuals.id("core/glow_particle"))
				.withFragmentShader(ElysiumVisuals.id("core/glow_particle"))
				.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
				.withCull(false)
				.withDepthStencilState(new DepthStencilState(depthTest, false))
				.build());
	}

	/** Depth-tested against the world (same compare op as vanilla), but never writes depth. */
	public static final RenderPipeline DEPTH_TESTED = pipeline("glow_particle", DepthStencilState.DEFAULT.depthTest());
	/** Visible through blocks. */
	public static final RenderPipeline THROUGH_WALLS = pipeline("glow_particle_xray", CompareOp.ALWAYS_PASS);

	private static final Map<ParticleTexture, RenderType> NORMAL = new EnumMap<>(ParticleTexture.class);
	private static final Map<ParticleTexture, RenderType> XRAY = new EnumMap<>(ParticleTexture.class);

	static {
		for (ParticleTexture t : ParticleTexture.values()) {
			NORMAL.put(t, create(t, DEPTH_TESTED, ""));
			XRAY.put(t, create(t, THROUGH_WALLS, "_xray"));
		}
	}

	private ParticleRenderTypes() {
	}

	private static RenderType create(ParticleTexture texture, RenderPipeline pipeline, String suffix) {
		return RenderType.create(ElysiumVisuals.MOD_ID + "_particle_" + texture.id() + suffix,
				RenderSetup.builder(pipeline)
						// Linear filtering keeps the soft sprites smooth when they are scaled up.
						.withTexture("Sampler0", texture.location(),
								() -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
						.createRenderSetup());
	}

	public static RenderType get(ParticleTexture texture, boolean throughWalls) {
		return (throughWalls ? XRAY : NORMAL).get(texture);
	}

	/** Forces class initialisation so the pipelines are registered before resources load. */
	public static void init() {
	}
}
