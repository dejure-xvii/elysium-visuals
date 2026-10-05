package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.elysium.visuals.client.module.impl.render.Ambience;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.Hands;
import dev.elysium.visuals.client.module.impl.render.MotionBlur;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Full-screen passes over the finished world image (before the hand and the
 * HUD), in a fixed order: sky → 3D clouds → Shaders → atmosphere → motion blur.
 */
public final class WorldEffects {
	private static TextureTarget scratch;

	private WorldEffects() {
	}

	/** Registers all pipelines; call during client init, before resources load. */
	public static void init() {
		CustomSky.init();
		CloudRenderer3D.init();
		ShadersRenderer.init();
		Ambience.init();
		MotionBlur.init();
		Hands.init();
		HudBlur.init();
	}

	/** Called (mixin) right after the world is drawn. */
	public static void afterWorld(GameRenderer renderer) {
		CustomSky.process(renderer);
		CloudRenderer3D.process(renderer);
		ShadersRenderer.process(renderer);
		Ambience.process(renderer);
		MotionBlur.process(renderer);
		// Last: the finished world, as the hand will be drawn over it.
		Hands.captureBackground(renderer);
	}

	/** A color target the size of {@code main}, shared by passes that can't read and write the same image. */
	public static TextureTarget scratch(RenderTarget main) {
		if (scratch == null) {
			scratch = new TextureTarget("elysium scratch", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
		} else if (scratch.width != main.width || scratch.height != main.height) {
			scratch.resize(main.width, main.height);
		}
		return scratch;
	}

	/** Copies {@code from} (same size) over the main color image. */
	public static void copyToMain(TextureTarget from, RenderTarget main) {
		RenderSystem.getDevice().createCommandEncoder()
				.copyTextureToTexture(from.getColorTexture(), main.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
	}

	/** 1 if NDC depth is 0..1 on this device (else -1..1); shaders need it to rebuild positions. */
	public static float zZeroToOne() {
		return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1f : 0f;
	}
}
