package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import dev.elysium.visuals.client.module.setting.StringSetting;
import dev.elysium.visuals.client.module.setting.Setting;

/** Creates the GUI widget for a setting type. Add a case here for new setting types. */
public final class SettingWidgets {
	private SettingWidgets() {
	}

	public static UiElement create(Setting<?> setting) {
		return switch (setting) {
			case BooleanSetting b -> new ToggleSwitch(b.name(), b::isOn, b::set);
			case NumberSetting n -> new SliderWidget(n);
			case ColorSetting c -> new ColorSettingWidget(c);
			case MultiSelectSetting m -> new MultiSelectWidget(m);
			case ModeSetting m -> new ModeWidget(m);
			case StringSetting s -> new TextSettingWidget(s);
			case StringListSetting s -> new StringListWidget(s);
			default -> throw new IllegalArgumentException("No widget for setting type " + setting.getClass());
		};
	}
}
