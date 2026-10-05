package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.Shaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Volumetric clouds (an effect of Shaders). After the world (and the custom sky) is
 * drawn, the clouds are raymarched at reduced resolution through a layer of
 * tileable Perlin-Worley noise, then upscaled with a depth-aware filter and
 * blended over the frame. While they are on, vanilla clouds aren't drawn.
 *
 * <p>Off with an Iris shader pack, and for the rest of the session after any
 * GPU error (vanilla clouds come back then).
 */
public final class CloudRenderer3D {
	/** All noise periods divide this many blocks, so the camera can be wrapped to it without seams. */
	private static final double WRAP = 24576;
	/** Wind speed at 1x, blocks per second. */
	private static final double WIND = 3.0;

	/** Resolution divider, primary steps, light steps, detail erosion — low / medium / high. */
	private static final int[][] QUALITY = {{4, 32, 2, 0}, {3, 48, 4, 1}, {2, 72, 6, 1}};

	private static FullscreenPass march;
	private static FullscreenPass composite;
	private static TextureTarget low;
	private static boolean failed;

	private static final Matrix4f invViewProj = new Matrix4f();
	private static double windX;
	private static double evolve;
	private static long lastMs;
	private static int frame;

	private CloudRenderer3D() {
	}

	/** Registers the pipelines before resources load. */
	public static void init() {
		march = new FullscreenPass("clouds", "core/clouds", List.of("DepthSampler", "NoiseSampler"), "CloudInfo",
				64 + 16 * 8, null);
		composite = new FullscreenPass("clouds_composite", "core/clouds_composite", List.of("CloudSampler", "DepthSampler"),
				"CompositeInfo", 64 + 16 * 2, BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
	}

	private static Shaders settings() {
		Shaders m = ModuleManager.get().find(Shaders.class);
		Minecraft mc = Minecraft.getInstance();
		boolean overworld = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
		return m != null && m.clouds3d() && overworld ? m : null;
	}

	/** 3D clouds are drawn this frame (so vanilla clouds must not be). */
	public static boolean active() {
		return !failed && settings() != null && !IrisCompat.shaderPackInUse() && CloudNoise.view() != null;
	}

	/** Called right after the world (and the custom sky) is drawn. */
	public static void process(GameRenderer renderer) {
		long now = Util.getMillis();
		float dt = lastMs == 0 ? 0f : Math.min(0.25f, (now - lastMs) / 1000f);
		lastMs = now;
		Shaders m = settings();
		if (m == null || failed) {
			return;
		}
		windX = (windX + dt * WIND * m.cloudWind()) % WRAP;
		evolve += dt * 0.08 * Math.max(0.2, m.cloudWind());
		if (!active()) {
			return;
		}
		try {
			render(renderer, m);
		} catch (Throwable t) {
			failed = true;
			ElysiumVisuals.LOGGER.error("3D clouds failed and are disabled until restart", t);
		}
	}

	private static void render(GameRenderer renderer, Shaders m) {
		RenderTarget main = renderer.mainRenderTarget();
		GpuTextureView color = main.getColorTextureView(), depth = main.getDepthTextureView();
		GpuTextureView noise = CloudNoise.view();
		if (color == null || depth == null || noise == null) {
			return;
		}
		int[] q = QUALITY[m.cloudQuality()];
		int lw = Math.max(1, main.width / q[0]), lh = Math.max(1, main.height / q[0]);
		if (low == null) {
			low = new TextureTarget("elysium clouds", lw, lh, false, GpuFormat.RGBA8_UNORM);
		} else if (low.width != lw || low.height != lh) {
			low.resize(lw, lh);
		}

		Minecraft mc = Minecraft.getInstance();
		CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
		camera.projectionMatrix.mul(camera.viewRotationMatrix, invViewProj).invert();
		Vec3 pos = camera.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float rain = mc.level.getRainLevel(partial), thunder = mc.level.getThunderLevel(partial);

		// Sun position from the client clock (Ambience can change it): 0 = sunrise in the east, 6000 = noon.
		long clock = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		double angle = clock / 24000.0 * Math.PI * 2;
		float sunX = (float) Math.cos(angle), sunY = (float) Math.sin(angle);
		// Same daylight curve as the custom sky, so clouds and sky agree.
		float day = CustomSky.daylight();
		float dusk = 1f - Math.abs(day * 2f - 1f);
		// High clouds stay lit a while after the sun has set.
		boolean sunUp = sunY > -0.2f;
		float lx = sunUp ? sunX : -sunX, ly = Math.max(Math.abs(sunY), 0.08f);
		float len = (float) Math.sqrt(lx * lx + ly * ly);

		// Light: white by day, warm pink-orange around sunset, faint cold moonlight at night; dimmer in rain.
		float sunStrength = smooth(-0.2f, 0.1f, sunY);
		float moonStrength = smooth(-0.2f, -0.45f, sunY);
		float[] warm = {1.0f, 0.5f, 0.36f}, white = {1.0f, 0.97f, 0.92f}, moon = {0.36f, 0.42f, 0.6f};
		float noon = smooth(0.0f, 0.5f, sunY);
		float weatherLight = 1f - 0.55f * rain - 0.2f * thunder;
		float[] light = new float[3];
		for (int i = 0; i < 3; i++) {
			float sun = (warm[i] + (white[i] - warm[i]) * noon) * 2.6f * sunStrength;
			light[i] = (sunUp ? sun : moon[i] * 0.6f * moonStrength) * weatherLight;
		}
		float[] top = new float[3], bottom = new float[3];
		float[] dayTop = {0.55f, 0.68f, 0.88f}, nightTop = {0.035f, 0.05f, 0.1f}, duskTop = {0.78f, 0.46f, 0.44f};
		for (int i = 0; i < 3; i++) {
			float v = nightTop[i] + (dayTop[i] - nightTop[i]) * day;
			v += (duskTop[i] - v) * dusk * 0.55f;
			v *= 1f - 0.35f * rain;
			top[i] = v;
			bottom[i] = v * (0.45f - 0.12f * rain);
		}
		float albedo = 1f - 0.4f * rain - 0.25f * thunder;

		float coverage = clamp01((float) m.cloudCoverage() + rain * 0.35f + thunder * 0.15f);
		float thickness = 110f * (1f + 0.4f * rain);
		float density = 1f + 0.8f * rain + 0.4f * thunder;
		float wrapX = (float) floorMod(pos.x, WRAP), wrapZ = (float) floorMod(pos.z, WRAP);
		float maxDist = (float) m.cloudDistance();
		int seed = frame++ % 64;
		float zz = WorldEffects.zZeroToOne();

		march.run(low.getColorTextureView(), new GpuTextureView[]{depth, noise}, new boolean[]{false, true},
				u -> u.putMat4f(invViewProj)
						.putVec4(wrapX, (float) pos.y, wrapZ, (float) evolve)
						.putVec4((float) m.cloudHeight(), thickness, coverage, density)
						.putVec4((float) windX, m.cloudsBlocky() ? 1f : 0f, (float) evolve, maxDist)
						.putVec4(lx / len, ly / len, 0f, day)
						.putVec4(light[0], light[1], light[2], albedo)
						.putVec4(top[0], top[1], top[2], q[1])
						.putVec4(bottom[0], bottom[1], bottom[2], q[2])
						.putVec4(zz, q[3], seed * 7.31f, 0.55f));
		composite.run(color, new GpuTextureView[]{low.getColorTextureView(), depth}, new boolean[]{false, false},
				u -> u.putMat4f(invViewProj)
						.putVec4(lw, lh, 1f / lw, 1f / lh)
						.putVec4(zz, 0f, 0f, 0f));
	}

	private static double floorMod(double v, double m) {
		double r = v % m;
		return r < 0 ? r + m : r;
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	private static float smooth(float a, float b, float x) {
		float t = clamp01((x - a) / (b - a));
		return t * t * (3 - 2 * t);
	}
}
