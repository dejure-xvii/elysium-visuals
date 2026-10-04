package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.StringSetting;

/** The Elysium main menu instead of the vanilla title screen. */
public class MainMenu extends Module {
	/** Chosen in the menu's background picker, not in the ClickGUI. */
	private final StringSetting background = add(new StringSetting("background", "Фон", "forest", 128)).visibleWhen(() -> false);
	private final NumberSetting spinSpeed = add(new NumberSetting("spin_speed", "Скорость вращения панорамы", 1, 0, 3, 0.1));

	public MainMenu() {
		super("main_menu", "MainMenu", "Главное меню Elysium вместо ванильного", Category.UTILS);
		enableByDefault();
	}

	public String background() {
		return background.get();
	}

	public void setBackground(String id) {
		background.set(id);
	}

	public float spinSpeed() {
		return (float) spinSpeed.get().doubleValue();
	}
}
