package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.FullscreenPass;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldEffects;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Shader sky: drawn over the pixels where the world left the sky visible. The
 * view direction comes from the camera's rotation and FOV, so the sky stays in
 * place while you look around.
 */
public class CustomSky extends Module {
	private static final List<String> MODES = List.of("aurora", "sakura", "plasma", "plasma2", "northern", "night", "summer", "caustics");
	private static FullscreenPass pass;

	private final ModeSetting mode = add(new ModeSetting("mode", "Режим",
			List.of(
					option("aurora", "Аврора"),
					option("sakura", "Сакура"),
					option("plasma", "Плазма"),
					option("plasma2", "Плазма 2"),
					option("northern", "Северное сияние"),
					option("night", "Ночное"),
					option("summer", "Летнее небо"),
					option("caustics", "Каустика")),
			"aurora"));
	private final NumberSetting speed = add(new NumberSetting("speed", "Скорость", 1, 0.1, 4, 0.1, "x"));
	private final NumberSetting scale = add(new NumberSetting("scale", "Масштаб", 1, 0.3, 3, 0.05, "x"));
	private final NumberSetting intensity = add(new NumberSetting("intensity", "Интенсивность", 1, 0.2, 2, 0.05));
	private final NumberSetting opacity = add(new NumberSetting("opacity", "Непрозрачность", 1, 0.05, 1, 0.05));
	private final BooleanSetting themed = add(new BooleanSetting("themed", "В цвет темы", false));

	private final Matrix4f invViewProj = new Matrix4f();
	private long startNs = System.nanoTime();

	public CustomSky() {
		super("custom_sky", "CustomSky", "Своё небо на шейдерах: аврора, сакура, плазма, звёзды…", Category.RENDER);
	}

	public static void init() {
		pass = new FullscreenPass("custom_sky", "core/custom_sky", List.of("DepthSampler"), "SkyInfo",
				64 + 16 * 4, BlendFunction.TRANSLUCENT);
	}

	public static void process(GameRenderer renderer) {
		CustomSky m = ModuleManager.get().find(CustomSky.class);
		Minecraft mc = Minecraft.getInstance();
		// The overworld sky only; the Nether and the End have no sky to replace.
		if (m != null && m.isEnabled() && mc.level != null && mc.level.dimension() == Level.OVERWORLD) {
			m.apply(renderer);
		}
	}

	/** 1 at noon, 0 at midnight, smooth in between (follows Ambience's time too). */
	private static float daylight() {
		Minecraft mc = Minecraft.getInstance();
		long t = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		double angle = (t - 6000) / 24000.0 * Math.PI * 2; // 0 at noon
		return (float) Math.max(0, Math.min(1, 0.5 + Math.cos(angle) * 0.9));
	}

	private void apply(GameRenderer renderer) {
		RenderTarget main = renderer.mainRenderTarget();
		GpuTextureView color = main.getColorTextureView(), depth = main.getDepthTextureView();
		if (color == null || depth == null) {
			return;
		}
		CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
		camera.projectionMatrix.mul(camera.viewRotationMatrix, invViewProj).invert();
		float time = (System.nanoTime() - startNs) / 1e9f * speed.floatValue();
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		int modeIndex = Math.max(0, MODES.indexOf(mode.get()));
		float day = daylight();
		pass.run(color, new GpuTextureView[]{depth}, new boolean[]{false},
				u -> u.putMat4f(invViewProj)
						.putVec4(time, scale.floatValue(), intensity.floatValue(), opacity.floatValue())
						.putVec4(ColorUtil.red(a) / 255f, ColorUtil.green(a) / 255f, ColorUtil.blue(a) / 255f, 1f)
						.putVec4(ColorUtil.red(b) / 255f, ColorUtil.green(b) / 255f, ColorUtil.blue(b) / 255f, 1f)
						.putVec4(modeIndex, themed.isOn() ? 0.85f : 0f, WorldEffects.zZeroToOne(), day));
	}
}
