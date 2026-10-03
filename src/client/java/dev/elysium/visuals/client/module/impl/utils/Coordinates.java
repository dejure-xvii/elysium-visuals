package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Locale;

/** Test module: player coordinates as a draggable HUD plate. */
public class Coordinates extends Module {
	private final BooleanSetting direction = add(new BooleanSetting("direction", "Сторона света", true));
	private final NumberSetting decimals = add(new NumberSetting("decimals", "Знаков после запятой", 1, 0, 3, 1));
	private final ColorSetting textColor = add(new ColorSetting("text_color", "Цвет текста", 0xFFFFFFFF));

	public Coordinates() {
		super("coordinates", "Coordinates", "Координаты игрока на экране", Category.UTILS);
		addHud(new LabelElement("coords", "Координаты", LabelElement.Anchor.TOP_LEFT) {
			@Override
			public boolean hasContent() {
				return Minecraft.getInstance().player != null;
			}

			@Override
			protected List<Segment> segments(boolean preview) {
				LocalPlayer player = Minecraft.getInstance().player;
				if (player == null) {
					return List.of(Segment.of("XYZ"));
				}
				String fmt = "%." + decimals.intValue() + "f";
				String coords = String.format(Locale.ROOT, fmt + "  " + fmt + "  " + fmt, player.getX(), player.getY(), player.getZ());
				return List.of(new Segment("XYZ", textColor.argb(), true), new Segment(coords, textColor.argb(), false),
						Segment.of(direction.isOn() ? directionName(player) : ""));
			}
		});
	}

	private static String directionName(LocalPlayer player) {
		return switch (player.getDirection()) {
			case NORTH -> "Север";
			case SOUTH -> "Юг";
			case WEST -> "Запад";
			case EAST -> "Восток";
			default -> "";
		};
	}
}
