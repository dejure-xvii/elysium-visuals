package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;

/** Current movement speed (blocks per second or km/h), also while riding. */
public class Speedometer extends Module {
	private final BooleanSetting kmh = add(new BooleanSetting("kmh", "Показывать в км/ч", false));
	private final BooleanSetting horizontal = add(new BooleanSetting("horizontal", "Только по горизонтали", true));
	private final BooleanSetting peak = add(new BooleanSetting("peak", "Максимальная скорость", false));
	private final ColorSetting color = add(new ColorSetting("color", "Цвет значения", 0xFF8FDBFF));

	/** Speed in blocks per second, smoothed over a few ticks. */
	private double speed;
	private double max;

	public Speedometer() {
		super("speedometer", "Speedometer", "Текущая скорость передвижения", Category.UTILS);
		addHud(new LabelElement("speed", "Скорость", LabelElement.Anchor.TOP_LEFT) {
			@Override
			public boolean hasContent() {
				return Minecraft.getInstance().player != null;
			}

			@Override
			protected List<Segment> segments(boolean preview) {
				return List.of(new Segment("Скорость", 0, true), new Segment(format(speed), color.argb(), false),
						Segment.of(peak.isOn() ? "макс " + format(max) : ""));
			}
		});
	}

	private String format(double bps) {
		return kmh.isOn()
				? String.format(Locale.ROOT, "%.1f км/ч", bps * 3.6)
				: String.format(Locale.ROOT, "%.2f б/с", bps);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			speed = 0;
			return;
		}
		Entity e = player.getVehicle() != null ? player.getVehicle() : player;
		double dx = e.getX() - e.xo, dy = horizontal.isOn() ? 0 : e.getY() - e.yo, dz = e.getZ() - e.zo;
		double now = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
		// Teleports would show absurd spikes.
		if (now > 200) {
			now = speed;
		}
		speed += (now - speed) * 0.5;
		max = Math.max(max, speed);
	}

	@Override
	protected void onEnable() {
		max = 0;
	}
}
