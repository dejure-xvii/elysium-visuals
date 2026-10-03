package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.util.Alerts;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/** Red vignette around the screen and a warning sound when health drops low. */
public class HealthAlert extends Module {
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Порог здоровья", 6, 1, 19, 1, " HP"));
	private final BooleanSetting vignette = add(new BooleanSetting("vignette", "Виньетка по краям", true));
	private final BooleanSetting pulse = add(new BooleanSetting("pulse", "Пульсация", true));
	private final NumberSetting size = add(new NumberSetting("size", "Ширина виньетки", 40, 10, 120, 5));
	private final ColorSetting color = add(new ColorSetting("color", "Цвет виньетки", 0xC0FF2020));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Звук при низком HP", true));

	/** True once the warning fired; re-armed when health recovers above the threshold. */
	private boolean warned;

	public HealthAlert() {
		super("health_alert", "Health Alert", "Предупреждает о низком здоровье виньеткой и звуком", Category.PLAYER);
	}

	private float danger(LocalPlayer player) {
		float hp = player.getHealth() + player.getAbsorptionAmount();
		float limit = threshold.floatValue();
		return hp >= limit || player.isDeadOrDying() ? 0f : 1f - hp / limit * 0.6f;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			warned = false;
			return;
		}
		float hp = player.getHealth() + player.getAbsorptionAmount();
		if (hp < threshold.floatValue() && !player.isDeadOrDying()) {
			if (!warned && sound.isOn()) {
				Alerts.ping(0.6f);
			}
			warned = true;
		} else if (hp >= threshold.floatValue() + 1) {
			warned = false;
		}
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!vignette.isOn() || player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		float d = danger(player);
		if (d <= 0f) {
			return;
		}
		if (pulse.isOn()) {
			d *= 0.7f + 0.3f * (float) Math.sin(Util.getMillis() / 180.0);
		}
		int c = ColorUtil.mulAlpha(color.argb(), d);
		int clear = ColorUtil.withAlpha(c, 0);
		int w = g.guiWidth(), h = g.guiHeight(), s = size.intValue();
		RenderUtil.fillGradient(g, 0, 0, w, s, c, clear);
		RenderUtil.fillGradient(g, 0, h - s, w, h, clear, c);
		RenderUtil.horizontalGradient(g, 0, 0, s, h, c, clear);
		RenderUtil.horizontalGradient(g, w - s, 0, s, h, clear, c);
	}
}
