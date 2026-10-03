package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Client-side atmosphere: time of day, color saturation and fog (vanilla fog
 * distances, or a soft blurred fog tinted with the theme). Nothing changes on
 * the server; fog is left alone under water, in lava or powder snow and with
 * blindness or darkness.
 */
public class Ambience extends Module {
	private static final long DAY = 24000;
	private static FullscreenPass pass;

	private final ModeSetting time = add(new ModeSetting("time", "Время суток",
			List.of(
					option("world", "Как в мире"),
					option("morning", "Утро"),
					option("day", "День"),
					option("evening", "Вечер"),
					option("night", "Ночь"),
					option("custom", "Своё")),
			"world"));
	private final NumberSetting hour = add(new NumberSetting("hour", "Час", 12, 0, 24, 0.25, " ч"))
			.visibleWhen(() -> time.is("custom"));
	private final NumberSetting saturation = add(new NumberSetting("saturation", "Насыщенность", 1, 0, 2.5, 0.05));
	private final ModeSetting fog = add(new ModeSetting("fog", "Туман",
			List.of(option("off", "Выкл"), option("normal", "Обычный"), option("blur", "Блюр")), "off"));
	private final NumberSetting fogStart = add(new NumberSetting("fog_start", "Начало тумана", 16, 0, 256, 1, " бл."))
			.visibleWhen(() -> fog.is("normal"));
	private final NumberSetting fogEnd = add(new NumberSetting("fog_end", "Конец тумана", 96, 4, 512, 1, " бл."))
			.visibleWhen(() -> fog.is("normal"));
	private final NumberSetting blurStrength = add(new NumberSetting("blur_strength", "Сила", 0.7, 0.05, 1, 0.05))
			.visibleWhen(() -> fog.is("blur"));
	private final NumberSetting blurDensity = add(new NumberSetting("blur_density", "Плотность", 1, 0.1, 3, 0.05))
			.visibleWhen(() -> fog.is("blur"));
	private final NumberSetting tint = add(new NumberSetting("tint", "Оттенок темы", 0.5, 0, 1, 0.05))
			.visibleWhen(() -> fog.is("blur"));

	private final Matrix4f invProj = new Matrix4f();

	public Ambience() {
		super("ambience", "Ambience", "Время суток, насыщенность и туман — только у тебя", Category.RENDER);
	}

	public static void init() {
		pass = new FullscreenPass("ambience", "core/ambience", List.of("MainSampler", "DepthSampler"),
				"AmbienceInfo", 64 + 16 * 3, null);
	}

	private static Ambience active() {
		Ambience m = ModuleManager.get().find(Ambience.class);
		return m != null && m.isEnabled() ? m : null;
	}

	// --- Time ---------------------------------------------------------------

	/** Called (mixin) for every clock read on the client: keeps the day number, replaces the time of day. */
	public static long overrideClock(long totalTicks) {
		Ambience m = active();
		if (m == null || m.time.is("world")) {
			return totalTicks;
		}
		long target = switch (m.time.get()) {
			case "morning" -> 1000;
			case "day" -> 6000;
			case "evening" -> 12500;
			case "night" -> 18000;
			// Tick 0 is 06:00.
			default -> Math.floorMod(Math.round((m.hour.get() - 6) * 1000), DAY);
		};
		return totalTicks - Math.floorMod(totalTicks, DAY) + target;
	}

	// --- Fog ----------------------------------------------------------------

	/** Fog is ours to change only in the open air, without blindness or darkness. */
	private static boolean fogAllowed() {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (camera.getFluidInCamera() != FogType.NONE) {
			return false;
		}
		return !(camera.entity() instanceof LivingEntity e)
				|| !(e.hasEffect(MobEffects.BLINDNESS) || e.hasEffect(MobEffects.DARKNESS));
	}

	/** Called (mixin) with the vanilla fog of this frame. */
	public static void modifyFog(FogData data) {
		Ambience m = active();
		if (m == null || !m.fog.is("normal") || !fogAllowed()) {
			return;
		}
		float start = m.fogStart.floatValue();
		float end = Math.max(start + 1, m.fogEnd.floatValue());
		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = Math.min(data.renderDistanceStart, start);
		data.renderDistanceEnd = Math.min(data.renderDistanceEnd, end);
		data.skyEnd = end;
		data.cloudEnd = end;
	}

	// --- Post pass (saturation + blur fog) ----------------------------------

	public static void process(GameRenderer renderer) {
		Ambience m = active();
		if (m == null || Minecraft.getInstance().level == null) {
			return;
		}
		boolean blur = m.fog.is("blur") && fogAllowed();
		if (!blur && Math.abs(m.saturation.get() - 1) < 0.01) {
			return;
		}
		m.apply(renderer, blur);
	}

	private void apply(GameRenderer renderer, boolean blur) {
		RenderTarget main = renderer.mainRenderTarget();
		GpuTextureView color = main.getColorTextureView(), depth = main.getDepthTextureView();
		if (color == null || depth == null) {
			return;
		}
		TextureTarget out = WorldEffects.scratch(main);
		CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
		camera.projectionMatrix.invert(invProj);
		int tintColor = ThemeColors.primary();
		float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
		pass.run(out.getColorTextureView(), new GpuTextureView[]{color, depth}, new boolean[]{true, false},
				b -> b.putMat4f(invProj)
						.putVec4(saturation.floatValue(), blur ? 1f : 0f, blurStrength.floatValue(), blurDensity.floatValue())
						.putVec4(ColorUtil.red(tintColor) / 255f, ColorUtil.green(tintColor) / 255f,
								ColorUtil.blue(tintColor) / 255f, tint.floatValue())
						.putVec4(WorldEffects.zZeroToOne(), renderDistance, 1f / main.width, 1f / main.height));
		WorldEffects.copyToMain(out, main);
	}
}
