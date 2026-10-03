package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Renders the world with a different aspect ratio than the window (the
 * "stretched resolution" look): wider ratios squeeze more into the view,
 * narrower ones stretch it.
 */
public class AspectRatio extends Module {
	private final ModeSetting ratio = add(new ModeSetting("ratio", "Соотношение",
			List.of(
					option("16:9", "16:9"),
					option("16:10", "16:10"),
					option("4:3", "4:3"),
					option("5:4", "5:4"),
					option("3:2", "3:2"),
					option("21:9", "21:9"),
					option("32:9", "32:9"),
					option("1:1", "1:1"),
					option("custom", "Своё")),
			"4:3"));
	private final NumberSetting custom = add(new NumberSetting("custom", "Своё значение", 1.33, 0.5, 4, 0.01))
			.visibleWhen(() -> ratio.is("custom"));

	public AspectRatio() {
		super("aspect_ratio", "AspectRatio", "Меняет соотношение сторон картинки (растянутое разрешение)", Category.RENDER);
	}

	/** The width/height ratio to render with, or null when the module is off. */
	public static Float current() {
		AspectRatio m = ModuleManager.get().find(AspectRatio.class);
		if (m == null || !m.isEnabled()) {
			return null;
		}
		String r = m.ratio.get();
		if (r.equals("custom")) {
			return m.custom.floatValue();
		}
		String[] parts = r.split(":");
		return Float.parseFloat(parts[0]) / Float.parseFloat(parts[1]);
	}
}
