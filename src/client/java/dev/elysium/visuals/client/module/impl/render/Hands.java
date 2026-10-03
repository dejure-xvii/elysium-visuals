package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.FullscreenPass;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldEffects;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Shader effects on the first-person hands and held item. The hand is found
 * by depth: the game clears the depth buffer right before drawing the hand, so
 * afterwards any written depth belongs to the hand. A full-screen pass then
 * fills, glasses, outlines or haloes it; the fire trail keeps a fading memory
 * of where the hand was.
 */
public class Hands extends Module {
	private static final List<String> MODES = List.of("fill", "glass", "plasma", "outline", "halo", "trail", "plasma_trail");
	private static FullscreenPass pass;
	private static FullscreenPass trailPass;

	private final ModeSetting mode = add(new ModeSetting("mode", "Режим",
			List.of(
					option("fill", "Fill"),
					option("glass", "Glass"),
					option("plasma", "Plasma"),
					option("outline", "Outline"),
					option("halo", "Halo"),
					option("trail", "Trail"),
					option("plasma_trail", "Plasma + Trail")),
			"plasma"));
	private final NumberSetting opacity = add(new NumberSetting("opacity", "Непрозрачность", 0.7, 0.05, 1, 0.05))
			.visibleWhen(() -> !mode.is("halo"));
	private final NumberSetting plasmaSpeed = add(new NumberSetting("plasma_speed", "Скорость плазмы", 1, 0.1, 4, 0.1, "x"))
			.visibleWhen(() -> mode.is("plasma") || mode.is("plasma_trail"));
	private final NumberSetting blur = add(new NumberSetting("blur", "Сила размытия", 0.6, 0.1, 1.5, 0.05))
			.visibleWhen(() -> mode.is("glass"));
	private final NumberSetting glowRadius = add(new NumberSetting("glow_radius", "Радиус свечения", 10, 2, 30, 1, " px"))
			.visibleWhen(() -> mode.is("halo"));
	private final NumberSetting glowStrength = add(new NumberSetting("glow_strength", "Сила свечения", 1, 0.1, 3, 0.05))
			.visibleWhen(() -> mode.is("halo"));
	private final NumberSetting trailSpeed = add(new NumberSetting("trail_speed", "Скорость шлейфа", 1, 0.1, 3, 0.05, "x"))
			.visibleWhen(() -> mode.is("trail") || mode.is("plasma_trail"));
	private final NumberSetting trailLength = add(new NumberSetting("trail_length", "Длина шлейфа", 0.5, 0.1, 1.5, 0.05))
			.visibleWhen(() -> mode.is("trail") || mode.is("plasma_trail"));
	private final NumberSetting outline = add(new NumberSetting("outline", "Толщина контура", 3, 1, 10, 0.5, " px"))
			.visibleWhen(() -> mode.is("outline"));

	private TextureTarget background;
	private TextureTarget historyA, historyB;
	private boolean historyValid;
	private long lastFrameNs;
	private final long startNs = System.nanoTime();

	public Hands() {
		super("hands", "Hands", "Шейдерные эффекты на руки: заливка, стекло, плазма, контур, свечение, шлейф", Category.RENDER);
	}

	public static void init() {
		pass = new FullscreenPass("hands", "core/hands",
				List.of("MainSampler", "DepthSampler", "BackgroundSampler", "HistorySampler"), "HandsInfo", 16 * 5, null);
		trailPass = new FullscreenPass("hands_trail", "core/hands_trail", List.of("DepthSampler", "HistorySampler"), "TrailInfo", 16, null);
	}

	private static Hands active() {
		Hands m = ModuleManager.get().find(Hands.class);
		return m != null && m.isEnabled() && Minecraft.getInstance().level != null ? m : null;
	}

	@Override
	protected void onDisable() {
		for (TextureTarget t : new TextureTarget[]{background, historyA, historyB}) {
			if (t != null) {
				t.destroyBuffers();
			}
		}
		background = historyA = historyB = null;
		historyValid = false;
	}

	private boolean trail() {
		return mode.is("trail") || mode.is("plasma_trail");
	}

	private TextureTarget sized(TextureTarget t, RenderTarget main, String name) {
		if (t == null) {
			return new TextureTarget(name, main.width, main.height, false, GpuFormat.RGBA8_UNORM);
		}
		if (t.width != main.width || t.height != main.height) {
			t.resize(main.width, main.height);
			historyValid = false;
		}
		return t;
	}

	/** Called (mixin) before the hand is drawn: remember the world behind it (for Glass). */
	public static void captureBackground(GameRenderer renderer) {
		Hands m = active();
		if (m == null || !m.mode.is("glass")) {
			return;
		}
		RenderTarget main = renderer.mainRenderTarget();
		m.background = m.sized(m.background, main, "elysium hands background");
		RenderSystem.getDevice().createCommandEncoder()
				.copyTextureToTexture(main.getColorTexture(), m.background.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
	}

	/** Called (mixin) right after the hand is drawn. */
	public static void process(GameRenderer renderer) {
		Hands m = active();
		if (m != null) {
			m.apply(renderer);
		}
	}

	private void apply(GameRenderer renderer) {
		RenderTarget main = renderer.mainRenderTarget();
		GpuTextureView color = main.getColorTextureView(), depth = main.getDepthTextureView();
		if (color == null || depth == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;

		boolean trail = trail();
		historyA = sized(historyA, main, "elysium hands trail A");
		historyB = sized(historyB, main, "elysium hands trail B");
		if (trail) {
			if (!historyValid) {
				RenderSystem.getDevice().createCommandEncoder().clearColorTexture(historyA.getColorTexture(), new org.joml.Vector4f(0, 0, 0, 1));
				historyValid = true;
			}
			// Fade by frame time: the trail lasts about "length" seconds at any FPS.
			float keep = (float) Math.exp(-dt / Math.max(0.02, trailLength.get() * 0.35));
			float rise = trailSpeed.floatValue() * dt * 0.12f;
			trailPass.run(historyB.getColorTextureView(), new GpuTextureView[]{depth, historyA.getColorTextureView()},
					new boolean[]{false, true}, b -> b.putVec4(keep, rise, 0, 0));
			TextureTarget swap = historyA;
			historyA = historyB;
			historyB = swap;
		} else {
			historyValid = false;
		}

		GpuTextureView bg = mode.is("glass") && background != null ? background.getColorTextureView() : color;
		TextureTarget out = WorldEffects.scratch(main);
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		int modeIndex = MODES.indexOf(mode.get());
		float time = (now - startNs) / 1e9f;
		pass.run(out.getColorTextureView(),
				new GpuTextureView[]{color, depth, bg, historyA.getColorTextureView()},
				new boolean[]{true, false, true, true},
				u -> u.putVec4(ColorUtil.red(a) / 255f, ColorUtil.green(a) / 255f, ColorUtil.blue(a) / 255f, 1)
						.putVec4(ColorUtil.red(b) / 255f, ColorUtil.green(b) / 255f, ColorUtil.blue(b) / 255f, 1)
						.putVec4(modeIndex, mode.is("halo") ? 1 : opacity.floatValue(), time, plasmaSpeed.floatValue())
						.putVec4(blur.floatValue(), glowRadius.floatValue(), glowStrength.floatValue(), outline.floatValue())
						.putVec4(1f / main.width, 1f / main.height, trail ? 1 : 0, 0));
		WorldEffects.copyToMain(out, main);
	}
}
