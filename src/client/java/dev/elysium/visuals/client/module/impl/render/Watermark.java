package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.hud.element.BindsElement;
import dev.elysium.visuals.client.hud.element.BuffsElement;
import dev.elysium.visuals.client.hud.element.CooldownsElement;
import dev.elysium.visuals.client.hud.element.FriendsElement;
import dev.elysium.visuals.client.hud.element.TargetElement;
import dev.elysium.visuals.client.hud.element.WatermarkElement;
import dev.elysium.visuals.client.hud.style.InterfaceStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Interface: a set of HUD elements picked from a multi-select list, all drawn
 * in one of four styles. Every element can be dragged around while the
 * ClickGUI is open. (The module id stays "watermark" so configs keep working.)
 */
public class Watermark extends Module {
	private final ModeSetting style = add(new ModeSetting("style", "Стиль", InterfaceStyle.options(),
			InterfaceStyle.DEFAULT.id()));
	private final BooleanSetting blur = add(new BooleanSetting("blur", "Блюр", true));
	private final NumberSetting blurStrength = add(new NumberSetting("blur_strength", "Сила размытия", 5, 1, 10, 1))
			.visibleWhen(blur::isOn);
	private final MultiSelectSetting elements = add(new MultiSelectSetting("elements", "Элементы HUD",
			List.of(
					option("watermark", "Ватермарка"),
					option("target", "Активный таргет"),
					option("binds", "Привязанные модули"),
					option("buffs", "Бафы"),
					option("cooldowns", "Задержки"),
					option("friends", "Список друзей")),
			Set.of("watermark", "target", "buffs", "cooldowns")));
	private final MultiSelectSetting info = add(new MultiSelectSetting("info", "Информация в ватермарке",
			List.of(
					option("fps", "FPS"),
					option("ping", "Пинг"),
					option("time", "Время"),
					option("server", "Сервер"),
					option("bps", "Скорость (BPS)"),
					option("coords", "Координаты")),
			Set.of("fps", "server")))
			// "Минимал" shows only the client name and the Minecraft version.
			.visibleWhen(() -> elements.isSelected("watermark") && !style.is(InterfaceStyle.MINIMAL.id()));
	private final BooleanSetting nick = add(new BooleanSetting("nick", "Ник в ватермарке", true))
			.visibleWhen(() -> elements.isSelected("watermark") && !style.is(InterfaceStyle.MINIMAL.id()));
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", "Секунды в часах", false))
			.visibleWhen(() -> info.isVisible() && info.isSelected("time"));

	public Watermark() {
		super("watermark", "Interface", "Ватермарка, таргет, бинды, эффекты, задержки и друзья в одном из 4 стилей",
				Category.RENDER);
		addHud(new WatermarkElement(seconds, nick, info)).visibleWhen(() -> elements.isSelected("watermark"));
		addHud(new BindsElement()).visibleWhen(() -> elements.isSelected("binds"));
		addHud(new FriendsElement()).visibleWhen(() -> elements.isSelected("friends"));
		addHud(new TargetElement()).visibleWhen(() -> elements.isSelected("target"));
		addHud(new CooldownsElement()).visibleWhen(() -> elements.isSelected("cooldowns"));
		addHud(new BuffsElement()).visibleWhen(() -> elements.isSelected("buffs"));
	}

	/** Blur the world under the plates (turned off automatically with Iris shaders). */
	public boolean blurEnabled() {
		return blur.isOn();
	}

	/** 1..10 */
	public int blurStrength() {
		return blurStrength.intValue();
	}

	/** Selected style; an unknown id (e.g. from an old config) falls back to the default. */
	public InterfaceStyle style() {
		return InterfaceStyle.byId(style.get());
	}
}
