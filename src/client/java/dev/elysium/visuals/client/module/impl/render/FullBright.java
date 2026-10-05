package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.client.renderer.state.LightmapRenderState;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Full brightness, only in the lightmap of this client: "night vision" lights
 * everything as the effect would (no effect is added, so there is no icon and
 * the server knows nothing), "gamma" pushes the brightness past the vanilla maximum.
 */
public class FullBright extends Module {
	private final ModeSetting mode = add(new ModeSetting("mode", "Режим",
			List.of(option("night_vision", "Ночное зрение"), option("gamma", "Гамма")), "night_vision"));
	private final NumberSetting gamma = add(new NumberSetting("gamma", "Гамма", 10, 1, 15, 0.5, "x"))
			.visibleWhen(() -> mode.is("gamma"));

	public FullBright() {
		super("full_bright", "FullBright", "Полная яркость: ночное зрение или повышенная гамма", Category.RENDER);
	}

	/** Called (mixin) after vanilla filled the lightmap state. */
	public static void apply(LightmapRenderState state) {
		FullBright m = ModuleManager.get().find(FullBright.class);
		if (m == null || !m.isEnabled()) {
			return;
		}
		if (m.mode.is("gamma")) {
			state.brightness = Math.max(state.brightness, m.gamma.floatValue());
		} else {
			state.nightVisionEffectIntensity = 1f;
		}
	}
}
