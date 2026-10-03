package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Server address, players online, ping (colored by quality) and time spent on the server. */
public class ServerInfo extends Module {
	private final BooleanSetting hideAddress = add(new BooleanSetting("hide_address", "Скрыть адрес (для стримов)", false));
	private final BooleanSetting online = add(new BooleanSetting("online", "Игроков онлайн", true));
	private final BooleanSetting ping = add(new BooleanSetting("ping", "Пинг", true));
	private final BooleanSetting playtime = add(new BooleanSetting("playtime", "Время на сервере", true));

	private ServerData joinedServer;
	private long joinedMs;

	public ServerInfo() {
		super("server_info", "Server Info", "Адрес сервера, онлайн, пинг и время игры", Category.UTILS);
		addHud(new LabelElement("server", "Сервер", LabelElement.Anchor.TOP_LEFT) {
			@Override
			public boolean hasContent() {
				return Minecraft.getInstance().getConnection() != null;
			}

			@Override
			protected List<Segment> segments(boolean preview) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.getConnection() == null) {
					return List.of(new Segment("mc.server.net", 0, true), Segment.of("42 онлайн"));
				}
				List<Segment> list = new ArrayList<>();
				ServerData server = mc.getCurrentServer();
				String address = server == null ? "Одиночная игра" : hideAddress.isOn() ? "Сервер" : server.ip;
				list.add(new Segment(address, 0, true));
				if (online.isOn()) {
					list.add(Segment.of(mc.getConnection().getOnlinePlayers().size() + " онлайн"));
				}
				if (ping.isOn() && mc.player != null && server != null) {
					PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
					if (info != null) {
						int ms = info.getLatency();
						int color = ms < 80 ? Palette.OK : ms < 180 ? Palette.WARN : Palette.DANGER;
						list.add(new Segment(ms + " ms", color, false));
					}
				}
				if (playtime.isOn()) {
					list.add(Segment.of(HudStyle.formatSeconds((int) ((Util.getMillis() - joinedMs) / 1000))));
				}
				return list;
			}
		});
	}

	@Override
	public void onTick(Minecraft mc) {
		// Restart the play timer whenever we join a different server or world.
		ServerData server = mc.getConnection() != null ? mc.getCurrentServer() : null;
		boolean connected = mc.getConnection() != null;
		if (!connected) {
			joinedServer = null;
			joinedMs = 0;
		} else if (joinedMs == 0 || server != joinedServer) {
			joinedServer = server;
			joinedMs = Util.getMillis();
		}
	}
}
