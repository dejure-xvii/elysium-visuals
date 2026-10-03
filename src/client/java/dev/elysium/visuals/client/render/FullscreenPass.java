package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * One full-screen shader pass (a single triangle covering the screen, like
 * vanilla post effects). The pipeline is immutable GPU state, so running it
 * never leaves anything changed for the rest of the frame.
 *
 * <p>The fragment shader gets {@code texCoord} from {@code minecraft:core/screenquad},
 * the samplers by the names given here, and one std140 uniform block.
 */
public final class FullscreenPass {
	/** GpuBuffer usage for a CPU-written uniform buffer (same as vanilla post passes). */
	private static final int UNIFORM_USAGE = 130;

	private final String name;
	private final RenderPipeline pipeline;
	private final List<String> samplers;
	private final String uniformBlock;
	private final int uniformSize;
	private MappableRingBuffer ubo;

	/**
	 * @param fragmentShader e.g. {@code core/motion_blur} in our namespace
	 * @param blend          null to overwrite the output, or a blend function
	 */
	public FullscreenPass(String name, String fragmentShader, List<String> samplers, String uniformBlock, int uniformSize,
						  BlendFunction blend) {
		this.name = name;
		this.samplers = List.copyOf(samplers);
		this.uniformBlock = uniformBlock;
		this.uniformSize = uniformSize;
		BindGroupLayout.Builder layout = BindGroupLayout.builder();
		for (String s : samplers) {
			layout.withSampler(s);
		}
		layout.withUniform(uniformBlock, UniformType.UNIFORM_BUFFER);
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(ElysiumVisuals.id("pipeline/" + name))
				.withVertexShader("core/screenquad")
				.withFragmentShader(ElysiumVisuals.id(fragmentShader))
				.withBindGroupLayout(layout.build());
		if (blend != null) {
			builder.withColorTargetState(new ColorTargetState(blend));
		}
		this.pipeline = RenderPipelines.register(builder.build());
	}

	/**
	 * Draws the pass into {@code output}.
	 *
	 * @param textures one view per sampler, in the order given to the constructor
	 * @param linear   per sampler: bilinear (true) or nearest (false) filtering
	 * @param uniforms writes the uniform block (std140)
	 */
	public void run(GpuTextureView output, GpuTextureView[] textures, boolean[] linear, Consumer<Std140Builder> uniforms) {
		if (ubo == null) {
			ubo = new MappableRingBuffer(() -> ElysiumVisuals.MOD_ID + " " + name, UNIFORM_USAGE, uniformSize);
		}
		try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
			uniforms.accept(Std140Builder.intoBuffer(view.data()));
		}
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> ElysiumVisuals.MOD_ID + " " + name, output, Optional.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform(uniformBlock, ubo.currentBuffer());
			for (int i = 0; i < samplers.size(); i++) {
				pass.bindTexture(samplers.get(i), textures[i],
						RenderSystem.getSamplerCache().getClampToEdge(linear[i] ? FilterMode.LINEAR : FilterMode.NEAREST));
			}
			pass.draw(3, 1, 0, 0);
		}
		ubo.rotate();
	}
}
