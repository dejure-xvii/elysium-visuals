package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.hud.element.BindsElement;
import dev.elysium.visuals.client.hud.element.BuffsElement;
import dev.elysium.visuals.client.hud.element.CooldownsElement;
import dev.elysium.visuals.client.hud.element.FriendsElement;
import dev.elysium.visuals.client.hud.element.TargetElement;
import dev.elysium.visuals.client.hud.element.WatermarkElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * HUD module: a set of elements picked from a multi-select list. Every
 * element can be dragged around while the ClickGUI is open.
 */
public class Watermark extends Module {
	private final MultiSelectSetting elements = add(new MultiSelectSetting("elements", "Элементы HUD",
			List.of(
					option("watermark", "Ватермарка"),
					option("target", "Активный таргет"),
					option("binds", "Привязанные модули"),
					option("buffs", "Бафы"),
					option("cooldowns", "Задержки"),
					option("friends", "Список друзей")),
			Set.of("watermark", "target", "buffs", "cooldowns")));
	private final BooleanSetting themeColor = add(new BooleanSetting("theme_color", "Цвет названия из темы", true));
	private final ColorSetting titleColor = add(new ColorSetting("color", "Цвет названия", 0xFF8FDBFF))
			.visibleWhen(() -> !themeColor.isOn());
	private final MultiSelectSetting info = add(new MultiSelectSetting("info", "Информация в ватермарке",
			List.of(
					option("fps", "FPS"),
					option("ping", "Пинг"),
					option("time", "Время"),
					option("server", "Сервер"),
					option("coords", "Координаты")),
			Set.of("fps", "ping", "time")))
			.visibleWhen(() -> elements.isSelected("watermark"));
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", "Секунды в часах", false));
	private final BooleanSetting nick = add(new BooleanSetting("nick", "Ник в ватермарке", true));
	private final ModeSetting targetStyle = add(new ModeSetting("target_style", "Стиль таргета",
			List.of(
					option("card", "Карточка"),
					option("compact", "Компактный"),
					option("minimal", "Минимал"),
					option("classic", "Классика"),
					option("capsule", "Капсула")),
			"card"))
			.visibleWhen(() -> elements.isSelected("target"));

	public Watermark() {
		super("watermark", "Watermark", "Ватермарка, таргет, бинды, эффекты, задержки и друзья", Category.RENDER);
		addHud(new WatermarkElement(() -> themeColor.isOn() ? 0 : titleColor.argb(), seconds, nick, info))
				.visibleWhen(() -> elements.isSelected("watermark"));
		addHud(new BindsElement()).visibleWhen(() -> elements.isSelected("binds"));
		addHud(new FriendsElement()).visibleWhen(() -> elements.isSelected("friends"));
		addHud(new TargetElement(targetStyle)).visibleWhen(() -> elements.isSelected("target"));
		addHud(new CooldownsElement()).visibleWhen(() -> elements.isSelected("cooldowns"));
		addHud(new BuffsElement()).visibleWhen(() -> elements.isSelected("buffs"));
	}
}
