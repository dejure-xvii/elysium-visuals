package dev.elysium.visuals.client.module.impl.farm;

import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/** Test module: how long the current farming session has been running. */
public class SessionTimer extends Module {
	private final BooleanSetting resetOnDisable = add(new BooleanSetting("reset_on_disable", "Сбрасывать при выключении", true));
	private final BooleanSetting showSeconds = add(new BooleanSetting("show_seconds", "Показывать секунды", true));
	private final ColorSetting color = add(new ColorSetting("color", "Цвет таймера", 0xFF9CFF8F));

	private long startMs;
	private long pausedTotalMs;

	public SessionTimer() {
		super("session_timer", "Session Timer", "Таймер текущей сессии фарма", Category.FARM);
		addHud(new LabelElement("timer", "Таймер фарма", LabelElement.Anchor.TOP_LEFT) {
			@Override
			protected List<Segment> segments(boolean preview) {
				return List.of(new Segment("Фарм", 0, true), new Segment(format(), color.argb(), false));
			}
		});
	}

	private String format() {
		long total = (pausedTotalMs + (isEnabled() ? Util.getMillis() - startMs : 0)) / 1000;
		return showSeconds.isOn()
				? String.format(Locale.ROOT, "%02d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
				: String.format(Locale.ROOT, "%02d:%02d", total / 3600, total / 60 % 60);
	}

	@Override
	protected void onEnable() {
		startMs = Util.getMillis();
	}

	@Override
	protected void onDisable() {
		pausedTotalMs = resetOnDisable.isOn() ? 0 : pausedTotalMs + (Util.getMillis() - startMs);
	}
}
