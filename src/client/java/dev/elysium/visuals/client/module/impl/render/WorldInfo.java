package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** In-game clock, day number, weather and whether it is dark enough for mobs. */
public class WorldInfo extends Module {
	private final BooleanSetting day = add(new BooleanSetting("day", "Номер дня", true));
	private final BooleanSetting weather = add(new BooleanSetting("weather", "Погода", true));
	private final BooleanSetting night = add(new BooleanSetting("night", "Предупреждение о ночи", true));
	private final ColorSetting timeColor = add(new ColorSetting("time_color", "Цвет времени", 0xFFFFD27A));

	public WorldInfo() {
		super("world_info", "World Info", "Игровое время, день, погода и наступление ночи", Category.RENDER);
		addHud(new LabelElement("world", "Мир", LabelElement.Anchor.TOP_LEFT) {
			@Override
			public boolean hasContent() {
				return Minecraft.getInstance().level != null;
			}

			@Override
			protected List<Segment> segments(boolean preview) {
				ClientLevel level = Minecraft.getInstance().level;
				if (level == null) {
					return List.of(new Segment("12:00", timeColor.argb(), true), Segment.of("День 1"));
				}
				List<Segment> list = new ArrayList<>();
				boolean overworld = level.dimension() == Level.OVERWORLD;
				long clock = level.getOverworldClockTime();
				if (overworld) {
					// Tick 0 is 06:00; a day is 24000 ticks.
					long t = (clock % 24000 + 24000) % 24000;
					long minutes = (t * 1440 / 24000 + 6 * 60) % 1440;
					list.add(new Segment(String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60), timeColor.argb(), true));
				} else {
					list.add(new Segment(level.dimension() == Level.NETHER ? "Незер" : "Энд", timeColor.argb(), true));
				}
				if (day.isOn()) {
					list.add(Segment.of("День " + (clock / 24000 + 1)));
				}
				if (weather.isOn() && overworld) {
					list.add(Segment.of(level.isThundering() ? "Гроза" : level.isRaining() ? "Дождь" : "Ясно"));
				}
				if (night.isOn() && overworld && level.isDarkOutside()) {
					list.add(new Segment("Ночь — мобы", 0xFFFF7070, false));
				}
				return list;
			}
		});
	}
}
