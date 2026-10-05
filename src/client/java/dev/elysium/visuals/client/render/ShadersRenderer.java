package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.Shaders;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * The passes of the Shaders module, run on the finished world image (after
 * the custom sky and the 3D clouds, before Ambience and motion blur):
 * <ol>
 *   <li>reflections and light (AO + light rays) at reduced resolution;</li>
 *   <li>composite at full resolution: AO, wet surfaces and puddles, reflections,
 *       light rays, sky glow, underwater fog;</li>
 *   <li>bloom (threshold at 1/2, dual-filter blur) and auto exposure (1x1, adapts over time);</li>
 *   <li>post at full resolution: depth of field, chromatic aberration, sharpening,
 *       bloom, exposure, tonemapping, saturation.</li>
 * </ol>
 * Passes whose effects are all off are skipped. Wetness fades in and dries over
 * about two seconds, also after the module is switched off. Off with an Iris
 * shader pack, and for the rest of the session after any GPU error.
 */
public final class ShadersRenderer {
	/** Uniform block: 2 matrices + 14 vec4. */
	private static final int UNIFORM_SIZE = 64 * 2 + 16 * 14;
	private static final double WRAP = 4096;
	/** Fade of the wetness, per second (~2 s to dry). */
	private static final float WET_SPEED = 2.2f;

	/** Per preset: effect resolution divider, reflection steps (0 = sky only), AO samples, ray samples, DoF taps, bloom levels. */
	private static final int[][] QUALITY = {{2, 8, 4, 12, 6, 2}, {2, 16, 8, 24, 12, 3}, {1, 32, 12, 36, 20, 4}};

	private static FullscreenPass reflect, light, composite, bloomPrefilter, bloomDown, bloomUp, exposure, post;
	private static TextureTarget reflectTarget, lightTarget, sceneCopy;
	private static final TextureTarget[] bloom = new TextureTarget[4];
	private static final TextureTarget[] exposureTargets = new TextureTarget[2];
	private static TextureTarget black;
	private static int exposureIndex;
	private static boolean failed;

	private static float wet;
	/** Puddle size under the drops, 0..1 (1 without drops). */
	private static float puddleGrowth = 1;
	/** Ripple strength on puddles (follows the drop density, fades). */
	private static float drops;
	private static boolean wasDropping;
	private static long lastMs;
	private static int frame;
	private static final Matrix4f invViewProj = new Matrix4f();
	private static final Matrix4f viewProj = new Matrix4f();

	private ShadersRenderer() {
	}

	public static void init() {
		reflect = pass("shaders_reflect", List.of("DepthSampler", "SceneSampler"));
		light = pass("shaders_light", List.of("DepthSampler"));
		composite = pass("shaders_composite", List.of("DepthSampler", "SceneSampler", "ReflectSampler", "LightSampler"));
		bloomPrefilter = pass("shaders_bloom", List.of("DepthSampler", "SceneSampler"));
		bloomDown = new FullscreenPass("shaders_bloom_down", "core/blur_down", List.of("InSampler"), "BlurInfo", 16, null);
		bloomUp = new FullscreenPass("shaders_bloom_up", "core/blur_up", List.of("InSampler"), "BlurInfo", 16, null);
		exposure = pass("shaders_exposure", List.of("DepthSampler", "SceneSampler", "PreviousSampler"));
		post = pass("shaders_post", List.of("DepthSampler", "SceneSampler", "BloomSampler", "ExposureSampler"));
	}

	private static FullscreenPass pass(String name, List<String> samplers) {
		return new FullscreenPass(name, "core/" + name, samplers, "ShaderInfo", UNIFORM_SIZE, null);
	}

	/** True while the module (or the fading wetness) has something to draw. */
	public static boolean active() {
		Shaders m = Shaders.get();
		return !failed && m != null && (m.isEnabled() || wet > 0.002f) && !IrisCompat.shaderPackInUse();
	}

	/** Called right after the world (and the custom sky and clouds) is drawn. */
	public static void process(GameRenderer renderer) {
		Shaders m = Shaders.get();
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		float dt = lastMs == 0 ? 0 : Math.min(0.25f, (now - lastMs) / 1000f);
		lastMs = now;
		if (m == null || failed || mc.level == null) {
			return;
		}
		boolean dropping = m.effect("drops") && !IrisCompat.shaderPackInUse();
		// Drops make the ground wet too, even without the "Мокрота" effect.
		boolean wetWanted = (m.effect("wet") || dropping) && !IrisCompat.shaderPackInUse();
		wet += ((wetWanted ? 1f : 0f) - wet) * (1f - (float) Math.exp(-WET_SPEED * dt));
		if (!wetWanted && wet < 0.002f) {
			wet = 0;
		}
		// Under the drops the puddles start from nothing and spread (~30 s at 1x); the ripples fade in and out.
		if (dropping) {
			if (!wasDropping) {
				puddleGrowth = 0;
			}
			puddleGrowth += (1f - puddleGrowth) * (1f - (float) Math.exp(-dt * m.puddleSpeed() / 12f));
		} else {
			puddleGrowth = 1;
		}
		wasDropping = dropping;
		drops += ((dropping ? m.dropsDensity() : 0f) - drops) * (1f - (float) Math.exp(-WET_SPEED * dt));
		if (!active()) {
			return;
		}
		try {
			render(renderer, m, dt);
		} catch (Throwable t) {
			failed = true;
			ElysiumVisuals.LOGGER.error("Shaders failed and are disabled until restart", t);
		}
	}

	/** True after a GPU error switched the passes off (for tests). */
	public static boolean failed() {
		return failed;
	}

	/** Puddle size under the drops 0..1 (for tests). */
	public static float puddleGrowth() {
		return puddleGrowth;
	}

	/** Wetness 0..1 as it fades in and dries (for tests). */
	public static float wetness() {
		return wet;
	}

	private static TextureTarget target(TextureTarget t, String name, int w, int h) {
		w = Math.max(1, w);
		h = Math.max(1, h);
		if (t == null) {
			return new TextureTarget(name, w, h, false, GpuFormat.RGBA8_UNORM);
		}
		if (t.width != w || t.height != h) {
			t.resize(w, h);
		}
		return t;
	}

	private record Frame(boolean reflections, boolean rays, boolean ao, boolean wet, boolean bloom, boolean tonemap,
						 boolean exposure, boolean underwater, boolean dof, boolean chromatic, boolean sharpen, boolean sky,
						 int[] q) {
		boolean anyComposite() {
			return reflections || rays || ao || wet || underwater || sky;
		}

		boolean anyPost() {
			return bloom || tonemap || exposure || dof || chromatic || sharpen;
		}
	}

	private static void render(GameRenderer renderer, Shaders m, float dt) {
		RenderTarget main = renderer.mainRenderTarget();
		GpuTextureView color = main.getColorTextureView(), depth = main.getDepthTextureView();
		if (color == null || depth == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean on = m.isEnabled();
		Camera camera = mc.gameRenderer.mainCamera();
		boolean underwater = camera.getFluidInCamera() == FogType.WATER;
		boolean overworld = mc.level.dimension() == Level.OVERWORLD;
		CustomSky customSky = ModuleManager.get().find(CustomSky.class);
		Frame f = new Frame(on && m.selected("reflections"), on && m.selected("rays") && overworld, on && m.selected("ao"),
				wet > 0.002f, on && m.selected("bloom"), on && m.selected("tonemap"), on && m.selected("exposure"),
				on && m.selected("underwater") && underwater, on && m.selected("dof"), on && m.selected("chromatic"),
				on && m.selected("sharpen"), on && m.selected("sky") && overworld && (customSky == null || !customSky.isEnabled()),
				QUALITY[m.qualityIndex()]);
		if (!f.anyComposite() && !f.anyPost()) {
			return;
		}

		CameraRenderState cam = renderer.gameRenderState().levelRenderState.cameraRenderState;
		cam.projectionMatrix.mul(cam.viewRotationMatrix, viewProj);
		viewProj.invert(invViewProj);
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 pos = cam.pos;

		// Sun (or moon) direction, same convention as the 3D clouds: 0 = sunrise in the east.
		long clock = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		double angle = clock / 24000.0 * Math.PI * 2;
		float sx = (float) Math.cos(angle), sy = (float) Math.sin(angle);
		boolean sunUp = sy > -0.05f;
		float lx = sunUp ? sx : -sx, ly = sunUp ? sy : -sy;
		float day = overworld ? CustomSky.daylight() : 0.3f;
		float rain = mc.level.getRainLevel(partial);
		float[] sun = sunUp ? new float[]{1.0f, 0.93f - 0.25f * (1 - Math.min(1, sy * 3)), 0.82f - 0.4f * (1 - Math.min(1, sy * 3))}
				: new float[]{0.32f, 0.38f, 0.55f};
		float sunPower = sunUp ? Math.min(1f, sy * 4f + 0.2f) : 0.35f * Math.min(1f, -sy * 4f);
		Vector4f sunClip = viewProj.transform(new Vector4f(lx * 1000, ly * 1000, 0, 1));
		float[] sunScreen = {0.5f, 0.5f, 0f};
		if (sunClip.w > 1e-3f) {
			float u = sunClip.x / sunClip.w * 0.5f + 0.5f, v = sunClip.y / sunClip.w * 0.5f + 0.5f;
			float outside = Math.max(Math.max(-u, u - 1), Math.max(-v, v - 1));
			sunScreen[0] = u;
			sunScreen[1] = v;
			sunScreen[2] = Math.max(0, 1 - Math.max(0, outside) * 2.5f) * (sunUp ? 1f : 0.35f) * (1 - rain * 0.8f);
		}
		int skyRgb = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partial);
		int waterRgb = camera.attributeProbe().getValue(EnvironmentAttributes.WATER_FOG_COLOR, partial);

		int[] q = f.q();
		float steps = q[1], aoSamples = q[2], raySamples = q[3], dofTaps = q[4];
		float zz = WorldEffects.zZeroToOne();
		int fr = frame++ & 1023;
		float wrapX = (float) floorMod(pos.x, WRAP), wrapZ = (float) floorMod(pos.z, WRAP);
		float time = (float) ((now() / 1000.0) % 3600.0);
		float texW = 1f / main.width, texH = 1f / main.height;

		java.util.function.Consumer<Std140Builder> base = u -> {
			u.putMat4f(invViewProj).putMat4f(viewProj)
					.putVec4(wrapX, (float) pos.y, wrapZ, time)
					.putVec4(texW, texH, zz, fr)
					.putVec4(lx, ly, 0f, day)
					.putVec4(sun[0] * sunPower, sun[1] * sunPower, sun[2] * sunPower, sunUp ? 1f : 0f)
					.putVec4(sunScreen[0], sunScreen[1], sunScreen[2], drops)
					.putVec4(ARGB.red(skyRgb) / 255f, ARGB.green(skyRgb) / 255f, ARGB.blue(skyRgb) / 255f, rain)
					.putVec4(ARGB.red(waterRgb) / 255f, ARGB.green(waterRgb) / 255f, ARGB.blue(waterRgb) / 255f, underwater ? 1f : 0f)
					.putVec4(m.reflectStrength(), m.reflectDistance(), m.raysStrength(), m.aoStrength())
					.putVec4(m.bloomStrength(), m.bloomThreshold(), m.exposureStrength(), on ? m.saturation() : 1f)
					.putVec4(wet, m.puddles() * puddleGrowth, m.qualityIndex(), m.wetStrength())
					.putVec4(b(f.reflections()), b(f.rays()), b(f.ao()), b(f.wet()))
					.putVec4(b(f.bloom()), b(f.tonemap()), b(f.exposure()), b(f.underwater()))
					.putVec4(b(f.dof()), b(f.chromatic()), b(f.sharpen()), b(f.sky()));
		};
		java.util.function.Consumer<Std140Builder> uniforms = u -> {
			base.accept(u);
			u.putVec4(steps, aoSamples, raySamples, dofTaps);
		};

		// 1. Reduced resolution: reflections and light.
		int div = q[0];
		int lw = main.width / div, lh = main.height / div;
		boolean needReflect = f.reflections() || f.wet();
		boolean needLight = f.ao() || f.rays();
		if (needReflect) {
			reflectTarget = target(reflectTarget, "elysium shaders reflect", lw, lh);
			reflect.run(reflectTarget.getColorTextureView(), new GpuTextureView[]{depth, color}, new boolean[]{false, true}, uniforms);
		}
		if (needLight) {
			lightTarget = target(lightTarget, "elysium shaders light", lw, lh);
			light.run(lightTarget.getColorTextureView(), new GpuTextureView[]{depth}, new boolean[]{false}, uniforms);
		}

		// 2. Composite at full resolution.
		if (f.anyComposite()) {
			copyScene(main);
			GpuTextureView none = black().getColorTextureView();
			composite.run(color, new GpuTextureView[]{depth, sceneCopy.getColorTextureView(),
							needReflect ? reflectTarget.getColorTextureView() : none, needLight ? lightTarget.getColorTextureView() : none},
					new boolean[]{false, false, true, true}, uniforms);
		}
		if (!f.anyPost()) {
			return;
		}

		// 3. Bloom and exposure.
		GpuTextureView bloomView = black().getColorTextureView();
		if (f.bloom()) {
			bloomView = runBloom(main, color, depth, q[5], uniforms);
		}
		GpuTextureView exposureView = black().getColorTextureView();
		if (f.exposure()) {
			exposureView = runExposure(color, depth, base, dt);
		}

		// 4. Post at full resolution.
		copyScene(main);
		post.run(color, new GpuTextureView[]{depth, sceneCopy.getColorTextureView(), bloomView, exposureView},
				new boolean[]{false, true, true, false}, uniforms);
	}

	private static GpuTextureView runBloom(RenderTarget main, GpuTextureView color, GpuTextureView depth, int levels,
										   java.util.function.Consumer<Std140Builder> uniforms) {
		bloom[0] = target(bloom[0], "elysium shaders bloom 0", main.width / 2, main.height / 2);
		bloomPrefilter.run(bloom[0].getColorTextureView(), new GpuTextureView[]{depth, color}, new boolean[]{false, true}, uniforms);
		for (int i = 1; i < levels; i++) {
			TextureTarget src = bloom[i - 1];
			bloom[i] = target(bloom[i], "elysium shaders bloom " + i, src.width / 2, src.height / 2);
			float tx = 1f / src.width, ty = 1f / src.height;
			bloomDown.run(bloom[i].getColorTextureView(), new GpuTextureView[]{src.getColorTextureView()}, new boolean[]{true},
					b -> b.putVec4(tx, ty, 1.2f, 0f));
		}
		for (int i = levels - 1; i > 0; i--) {
			TextureTarget from = bloom[i], to = bloom[i - 1];
			float tx = 1f / from.width, ty = 1f / from.height;
			bloomUp.run(to.getColorTextureView(), new GpuTextureView[]{from.getColorTextureView()}, new boolean[]{true},
					b -> b.putVec4(tx, ty, 1.2f, 0f));
		}
		return bloom[0].getColorTextureView();
	}

	private static GpuTextureView runExposure(GpuTextureView color, GpuTextureView depth,
											  java.util.function.Consumer<Std140Builder> base, float dt) {
		for (int i = 0; i < 2; i++) {
			if (exposureTargets[i] == null) {
				exposureTargets[i] = target(null, "elysium shaders exposure " + i, 1, 1);
				// Start at exposure 1.0 (stored as 1/4 = 64/255).
				RenderSystem.getDevice().createCommandEncoder().clearColorTexture(exposureTargets[i].getColorTexture(),
						new Vector4f(64f / 255f, 0f, 0f, 1f));
			}
		}
		TextureTarget prev = exposureTargets[exposureIndex], next = exposureTargets[1 - exposureIndex];
		float adapt = 1f - (float) Math.exp(-1.6f * dt);
		exposure.run(next.getColorTextureView(), new GpuTextureView[]{depth, color, prev.getColorTextureView()},
				new boolean[]{false, true, false}, u -> {
					base.accept(u);
					u.putVec4(0f, 0f, 0f, adapt);
				});
		exposureIndex = 1 - exposureIndex;
		return next.getColorTextureView();
	}

	private static void copyScene(RenderTarget main) {
		sceneCopy = target(sceneCopy, "elysium shaders scene", main.width, main.height);
		RenderSystem.getDevice().createCommandEncoder()
				.copyTextureToTexture(main.getColorTexture(), sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
	}

	/** A 1x1 black texture for samplers of passes that were skipped. */
	private static TextureTarget black() {
		if (black == null) {
			black = target(null, "elysium shaders black", 1, 1);
			RenderSystem.getDevice().createCommandEncoder().clearColorTexture(black.getColorTexture(), new Vector4f(0f, 0f, 0f, 0f));
		}
		return black;
	}

	private static float b(boolean v) {
		return v ? 1f : 0f;
	}

	private static long now() {
		return Util.getMillis();
	}

	private static double floorMod(double v, double m) {
		double r = v % m;
		return r < 0 ? r + m : r;
	}
}
