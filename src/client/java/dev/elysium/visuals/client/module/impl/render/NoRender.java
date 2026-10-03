package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/** Hides distracting vanilla effects. The checks are called from mixins and the HUD manager. */
public class NoRender extends Module {
	private final MultiSelectSetting hide = add(new MultiSelectSetting("hide", "Убрать",
			List.of(
					option("fire", "Огонь на экране"),
					option("hurt_cam", "Тряска камеры при уроне"),
					option("particles", "Частицы"),
					option("totem", "Анимация тотема"),
					option("scoreboard", "Скорборд"),
					option("titles", "Тайтлы"),
					option("camera_clip", "Камера сквозь блоки (3-е лицо)")),
			Set.of("fire", "hurt_cam", "totem")));

	public NoRender() {
		super("no_render", "NoRender", "Убирает лишнее с экрана: огонь, тряску, частицы, тотем…", Category.RENDER);
	}

	/** True if NoRender is on and hides {@code what} (an option id). */
	public static boolean hides(String what) {
		NoRender m = ModuleManager.get().find(NoRender.class);
		return m != null && m.isEnabled() && m.hide.isSelected(what);
	}

	@Override
	public boolean hidesVanillaHud(Identifier element) {
		return element.equals(VanillaHudElements.SCOREBOARD) && hide.isSelected("scoreboard")
				|| element.equals(VanillaHudElements.TITLE_AND_SUBTITLE) && hide.isSelected("titles");
	}
}
