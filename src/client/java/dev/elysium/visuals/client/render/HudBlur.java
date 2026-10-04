package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.Watermark;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Frosted background under the Interface plates. While the HUD is built, every
 * plate adds its rounded rectangle to a mask ({@link #mask}). Once per frame,
 * right before the GUI is drawn, the world image is blurred at low resolution
 * (dual-filter Kawase) and composited only inside the mask; the plates' own
 * translucent fill is then drawn on top as usual. No mask (or blur off) = no passes.
 *
 * <p>Switches itself off while an Iris shader pack is active, and for the rest
 * of the session if the GPU work ever fails.
 */
public final class HudBlur {
	private static final int MAX_RECTS = 64;
	private static final int MAX_LEVELS = 4;

	private static FullscreenPass down;
	private static FullscreenPass up;
	private static FullscreenPass composite;

	/** x, y, w, h, radius (GUI units after the pose), opacity. */
	private static final List<float[]> rects = new ArrayList<>();
	private static final TextureTarget[] levels = new TextureTarget[MAX_LEVELS];
	private static boolean failed;

	private static Method irisInstance;
	private static Method irisShaderPackInUse;
	private static boolean irisLookedUp;

	private HudBlur() {
	}

	/** Registers the pipelines before resources load. */
	public static void init() {
		down = new FullscreenPass("hud_blur_down", "core/blur_down", List.of("InSampler"), "BlurInfo", 16, null);
		up = new FullscreenPass("hud_blur_up", "core/blur_up", List.of("InSampler"), "BlurInfo", 16, null);
		composite = new FullscreenPass("hud_blur_mask", "core/blur_mask", List.of("BlurSampler"), "MaskInfo",
				16 + MAX_RECTS * 16 * 2, BlendFunction.TRANSLUCENT);
	}

	private static Watermark settings() {
		Watermark m = ModuleManager.get().find(Watermark.class);
		return m != null && m.isEnabled() && m.blurEnabled() ? m : null;
	}

	/** True while plates should register themselves. */
	public static boolean active() {
		return !failed && settings() != null && !irisShadersOn();
	}

	/** Adds a rounded rectangle (in the current pose) to this frame's blur mask. */
	public static void mask(GuiGraphicsExtractor g, float x, float y, float w, float h, float r) {
		float alpha = RenderUtil.alpha();
		if (alpha < 0.01f || w <= 0 || h <= 0 || rects.size() >= MAX_RECTS || !active()) {
			return;
		}
		Matrix3x2f pose = g.pose();
		Vector2f a = pose.transformPosition(x, y, new Vector2f());
		Vector2f b = pose.transformPosition(x + w, y + h, new Vector2f());
		float scale = Math.abs(b.x - a.x) / w;
		rects.add(new float[]{Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y),
				r * scale, alpha});
	}

	private static boolean irisShadersOn() {
		if (!irisLookedUp) {
			irisLookedUp = true;
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				try {
					Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
					irisInstance = api.getMethod("getInstance");
					irisShaderPackInUse = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException | LinkageError e) {
					ElysiumVisuals.LOGGER.warn("Iris API not found; HUD blur stays on with shaders", e);
				}
			}
		}
		if (irisShaderPackInUse == null) {
			return false;
		}
		try {
			return (boolean) irisShaderPackInUse.invoke(irisInstance.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true; // can't tell: be safe
		}
	}

	/** Called (mixin) right before the GUI is rendered; consumes this frame's mask. */
	public static void apply(GameRenderer renderer) {
		if (rects.isEmpty()) {
			return;
		}
		try {
			Watermark m = settings();
			if (m != null && !failed && !irisShadersOn()) {
				blur(renderer.mainRenderTarget(), m.blurStrength());
			}
		} catch (Throwable t) {
			failed = true;
			ElysiumVisuals.LOGGER.error("HUD blur failed and is disabled until restart", t);
		} finally {
			rects.clear();
		}
	}

	private static TextureTarget level(int i, int w, int h) {
		TextureTarget t = levels[i];
		if (t == null) {
			t = levels[i] = new TextureTarget("elysium hud blur " + i, w, h, false, GpuFormat.RGBA8_UNORM);
		} else if (t.width != w || t.height != h) {
			t.resize(w, h);
		}
		return t;
	}

	/** @param strength 1..10 */
	private static void blur(RenderTarget main, int strength) {
		GpuTextureView color = main.getColorTextureView();
		if (color == null) {
			return;
		}
		int count = strength >= 8 ? 4 : strength >= 4 ? 3 : 2;
		float offset = 0.8f + (strength - 1) * 0.12f;

		// Down: main -> 1/2 -> 1/4 ...
		GpuTextureView src = color;
		int sw = main.width, sh = main.height;
		for (int i = 0; i < count; i++) {
			TextureTarget dst = level(i, Math.max(1, sw / 2), Math.max(1, sh / 2));
			float tx = 1f / sw, ty = 1f / sh;
			down.run(dst.getColorTextureView(), new GpuTextureView[]{src}, new boolean[]{true},
					b -> b.putVec4(tx, ty, offset, 0f));
			src = dst.getColorTextureView();
			sw = dst.width;
			sh = dst.height;
		}
		// Up: ... -> 1/4 -> 1/2
		for (int i = count - 1; i > 0; i--) {
			TextureTarget from = levels[i], to = levels[i - 1];
			float tx = 1f / from.width, ty = 1f / from.height;
			up.run(to.getColorTextureView(), new GpuTextureView[]{from.getColorTextureView()}, new boolean[]{true},
					b -> b.putVec4(tx, ty, offset, 0f));
		}

		// Composite inside the mask. Rects go from GUI units (top-left origin) to pixels (bottom-left origin).
		float gui = Minecraft.getInstance().getWindow().getGuiScale();
		int width = main.width, height = main.height;
		composite.run(color, new GpuTextureView[]{levels[0].getColorTextureView()}, new boolean[]{true}, b -> {
			b.putVec4(width, height, rects.size(), 0f);
			for (int i = 0; i < MAX_RECTS; i++) {
				if (i < rects.size()) {
					float[] r = rects.get(i);
					b.putVec4(r[0] * gui, height - (r[1] + r[3]) * gui, r[2] * gui, r[3] * gui);
				} else {
					b.putVec4(0f, 0f, 0f, 0f);
				}
			}
			for (int i = 0; i < MAX_RECTS; i++) {
				if (i < rects.size()) {
					float[] r = rects.get(i);
					b.putVec4(r[4] * gui, r[5], 0f, 0f);
				} else {
					b.putVec4(0f, 0f, 0f, 0f);
				}
			}
		});
	}
}
