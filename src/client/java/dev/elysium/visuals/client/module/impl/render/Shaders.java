package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.notify.Notifications;
import dev.elysium.visuals.client.render.IrisCompat;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Built-in shaders: screen-space effects over the finished world image
 * ({@link dev.elysium.visuals.client.render.ShadersRenderer}) and the volumetric
 * clouds ({@link dev.elysium.visuals.client.render.CloudRenderer3D}). Each effect
 * is picked separately; its sliders show only while it is on. The quality preset
 * sets the resolution and sample counts. With an Iris shader pack the module
 * switches itself off (the pack has its own effects).
 */
public class Shaders extends Module {
	private final MultiSelectSetting effects = add(new MultiSelectSetting("effects", "Эффекты",
			List.of(option("reflections", "Отражения"),
					option("sky", "Небо"),
					option("clouds", "Объёмные облака"),
					option("rays", "Лучи света"),
					option("ao", "Затенение (AO)"),
					option("bloom", "Блум"),
					option("tonemap", "Тонмаппинг"),
					option("exposure", "Автоэкспозиция"),
					option("underwater", "Подводный туман"),
					option("dof", "Глубина резкости"),
					option("chromatic", "Хроматическая аберрация"),
					option("sharpen", "Резкость"),
					option("wet", "Мокрота")),
			Set.of("reflections", "sky", "clouds", "rays", "ao", "bloom", "tonemap")))
			.disableWhen("sky", () -> customSkyOn() ? "небо рисует CustomSky" : null);
	private final ModeSetting quality = add(new ModeSetting("quality", "Качество",
			List.of(option("low", "Низкое"), option("medium", "Среднее"), option("high", "Высокое")), "medium"));

	private final NumberSetting reflectStrength = add(new NumberSetting("reflect_strength", "Сила отражений", 0.6, 0, 1, 0.05))
			.visibleWhen(() -> effects.isSelected("reflections"));
	private final NumberSetting reflectDistance = add(new NumberSetting("reflect_distance", "Дальность отражений", 48, 8, 128, 4, " бл."))
			.visibleWhen(() -> effects.isSelected("reflections") || effects.isSelected("wet"));
	private final NumberSetting raysStrength = add(new NumberSetting("rays_strength", "Сила лучей", 0.6, 0.05, 1.5, 0.05))
			.visibleWhen(() -> effects.isSelected("rays"));
	private final NumberSetting aoStrength = add(new NumberSetting("ao_strength", "Сила затенения", 0.7, 0.05, 1, 0.05))
			.visibleWhen(() -> effects.isSelected("ao"));
	private final NumberSetting bloomStrength = add(new NumberSetting("bloom_strength", "Сила блума", 0.5, 0.05, 1.5, 0.05))
			.visibleWhen(() -> effects.isSelected("bloom"));
	private final NumberSetting bloomThreshold = add(new NumberSetting("bloom_threshold", "Порог блума", 0.85, 0.3, 0.98, 0.01))
			.visibleWhen(() -> effects.isSelected("bloom"));
	private final NumberSetting exposureStrength = add(new NumberSetting("exposure_strength", "Сила автоэкспозиции", 0.6, 0.05, 1, 0.05))
			.visibleWhen(() -> effects.isSelected("exposure"));
	private final NumberSetting wetStrength = add(new NumberSetting("wet_strength", "Сила мокроты", 0.7, 0.05, 1, 0.05))
			.visibleWhen(() -> effects.isSelected("wet"));
	private final NumberSetting puddles = add(new NumberSetting("puddles", "Количество луж", 0.5, 0, 1, 0.05))
			.visibleWhen(() -> effects.isSelected("wet"));
	private final NumberSetting saturation = add(new NumberSetting("saturation", "Насыщенность", 1, 0, 2, 0.05));

	// Clouds (moved here from CustomSky).
	private final ModeSetting cloudStyle = add(new ModeSetting("cloud_style", "Стиль облаков",
			List.of(option("natural", "Натуральные"), option("blocky", "Блочные")), "natural"))
			.visibleWhen(() -> effects.isSelected("clouds"));
	private final NumberSetting cloudHeight = add(new NumberSetting("cloud_height", "Высота облаков", 192, 128, 320, 4, " бл."))
			.visibleWhen(() -> effects.isSelected("clouds"));
	private final NumberSetting cloudCoverage = add(new NumberSetting("cloud_coverage", "Покрытие неба", 0.5, 0.05, 1, 0.05))
			.visibleWhen(() -> effects.isSelected("clouds"));
	private final NumberSetting cloudWind = add(new NumberSetting("cloud_wind", "Скорость ветра", 1, 0, 3, 0.1, "x"))
			.visibleWhen(() -> effects.isSelected("clouds"));
	private final NumberSetting cloudDistance = add(new NumberSetting("cloud_distance", "Дальность облаков", 1024, 256, 2048, 64, " бл."))
			.visibleWhen(() -> effects.isSelected("clouds"));

	private boolean migrated;

	public Shaders() {
		super("shaders", "Shaders", "Встроенные шейдеры: отражения, лучи, AO, блум, мокрота, 3D-облака…", Category.RENDER);
	}

	public static Shaders get() {
		return ModuleManager.get().find(Shaders.class);
	}

	private static boolean customSkyOn() {
		CustomSky sky = ModuleManager.get().find(CustomSky.class);
		return sky != null && sky.isEnabled();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (IrisCompat.shaderPackInUse()) {
			setEnabled(false);
			Notifications.alert("Shaders", "Выключен: активен шейдерпак Iris");
		}
	}

	@Override
	protected void onEnable() {
		if (IrisCompat.shaderPackInUse()) {
			Notifications.alert("Shaders", "Не работает вместе с шейдерпаком Iris");
		}
	}

	/** Takes over the 3D clouds of an old config (they were a CustomSky option) once. */
	public void migrateClouds(CustomSky.LegacyClouds legacy) {
		if (migrated || legacy == null) {
			return;
		}
		migrated = true;
		java.util.Set<String> e = new java.util.LinkedHashSet<>(effects.get());
		e.add("clouds");
		effects.set(e);
		cloudHeight.set(legacy.height());
		cloudCoverage.set(legacy.coverage());
		cloudWind.set(legacy.wind());
		cloudDistance.set(legacy.distance());
		if (!isEnabled()) {
			setEnabled(true);
		}
	}

	/** On, and the effect is selected (and not covered by something else). */
	public boolean effect(String id) {
		return isEnabled() && effects.isActive(id);
	}

	/** Selected in the list, whether or not the module is on (wetness keeps drying after it's off). */
	public boolean selected(String id) {
		return effects.isActive(id);
	}

	/** 0 = low, 1 = medium, 2 = high. */
	public int qualityIndex() {
		return quality.is("low") ? 0 : quality.is("high") ? 2 : 1;
	}

	public float reflectStrength() {
		return reflectStrength.floatValue();
	}

	public float reflectDistance() {
		return reflectDistance.floatValue();
	}

	public float raysStrength() {
		return raysStrength.floatValue();
	}

	public float aoStrength() {
		return aoStrength.floatValue();
	}

	public float bloomStrength() {
		return bloomStrength.floatValue();
	}

	public float bloomThreshold() {
		return bloomThreshold.floatValue();
	}

	public float exposureStrength() {
		return exposureStrength.floatValue();
	}

	public float wetStrength() {
		return wetStrength.floatValue();
	}

	public float puddles() {
		return puddles.floatValue();
	}

	public float saturation() {
		return saturation.floatValue();
	}

	// --- Clouds (read by CloudRenderer3D) ----------------------------------------------------

	public boolean clouds3d() {
		return effect("clouds");
	}

	public boolean cloudsBlocky() {
		return cloudStyle.is("blocky");
	}

	public double cloudHeight() {
		return cloudHeight.get();
	}

	public double cloudCoverage() {
		return cloudCoverage.get();
	}

	public double cloudWind() {
		return cloudWind.get();
	}

	/** 0 = low, 1 = medium, 2 = high: follows the quality preset. */
	public int cloudQuality() {
		return qualityIndex();
	}

	public double cloudDistance() {
		return cloudDistance.get();
	}
}
