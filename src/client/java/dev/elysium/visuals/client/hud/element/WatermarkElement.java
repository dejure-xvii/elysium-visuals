package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.style.HudData;
import dev.elysium.visuals.client.hud.style.InterfaceStyle;
import dev.elysium.visuals.client.hud.style.Skin;
import dev.elysium.visuals.client.module.impl.utils.NameProtect;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client name, Minecraft version and the selected info (nick, FPS, ping, time, server, speed, coordinates). */
public class WatermarkElement extends HudElement {
	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final String CLIENT = "Elysium";

	private final BooleanSetting seconds;
	private final BooleanSetting showNick;
	private final MultiSelectSetting info;
	private HudData.Watermark data = new HudData.Watermark(CLIENT, "", List.of());

	public WatermarkElement(BooleanSetting seconds, BooleanSetting showNick, MultiSelectSetting info) {
		super("watermark", "Ватермарка", Anchor.TOP_LEFT);
		this.seconds = seconds;
		this.showNick = showNick;
		this.info = info;
	}

	private List<HudData.Part> collect() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		List<HudData.Part> list = new ArrayList<>();
		if (showNick.isOn()) {
			HudData.Lead head = player != null ? HudData.Lead.head(player.getSkin()) : HudData.Lead.NONE;
			// NameProtect replaces the nick unless it is told to leave the watermark alone (raw).
			list.add(new HudData.Part("nick", HudIcon.PLAYER, "Ник", mc.getUser().getName(),
					!NameProtect.hidesInWatermark(), head));
		}
		if (info.isSelected("fps")) {
			list.add(part("fps", HudIcon.FPS, "FPS", String.valueOf(mc.getFps())));
		}
		if (info.isSelected("ping")) {
			String ping = "—";
			if (mc.getConnection() != null && player != null) {
				PlayerInfo pi = mc.getConnection().getPlayerInfo(player.getUUID());
				if (pi != null) {
					ping = String.valueOf(pi.getLatency());
				}
			}
			list.add(part("ping", HudIcon.PING, "Пинг", ping));
		}
		if (info.isSelected("time")) {
			list.add(part("time", HudIcon.TIME, "Время", LocalTime.now().format(seconds.isOn() ? HH_MM_SS : HH_MM)));
		}
		if (info.isSelected("server")) {
			ServerData server = mc.getCurrentServer();
			String name = mc.getConnection() == null ? "Меню" : server == null ? "Одиночная игра" : server.ip;
			list.add(part("server", HudIcon.SERVER, "Сервер", name));
		}
		if (info.isSelected("bps") && player != null) {
			double bps = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 20;
			list.add(part("bps", HudIcon.SPEED, "Скорость", String.format(Locale.ROOT, "%.1f", bps)));
		}
		if (info.isSelected("coords") && player != null) {
			list.add(part("coords", HudIcon.COORDS, "Координаты",
					"x:" + player.getBlockX() + " y:" + player.getBlockY() + " z:" + player.getBlockZ()));
		}
		return list;
	}

	private static HudData.Part part(String id, HudIcon icon, String label, String value) {
		return new HudData.Part(id, icon, label, value, false, HudData.Lead.NONE);
	}

	@Override
	protected void measure(boolean preview) {
		data = new HudData.Watermark(CLIENT, SharedConstants.getCurrentVersion().name(), collect());
		Skin.Size size = InterfaceStyle.current().measureWatermark(data);
		width = animateWidth(size.width());
		height = animateHeight(size.height());
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		InterfaceStyle.current().drawWatermark(g, p, data, x, y, width, height);
	}
}
