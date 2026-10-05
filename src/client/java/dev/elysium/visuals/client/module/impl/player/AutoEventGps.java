package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.gps.EventPatterns;
import dev.elysium.visuals.client.gps.GpsTarget;
import dev.elysium.visuals.client.mixin.BossHealthOverlayAccessor;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.Gps;
import dev.elysium.visuals.client.module.setting.ActionSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Sets the GPS mark on server events: reads boss bars and chat, finds an
 * event and its coordinates ({@link EventPatterns}) and marks it as {@code .gps}
 * would, with a notification. The same event at the same place is marked once.
 */
public class AutoEventGps extends Module {
	private final ModeSetting server = add(new ModeSetting("server", "Сервер", serverOptions(), EventPatterns.SERVERS.getFirst().id()));
	private final MultiSelectSetting events = add(new MultiSelectSetting("events", "Ивенты", eventOptions(), allEvents()));
	@SuppressWarnings("unused")
	private final ActionSetting clear = add(new ActionSetting("clear", () -> "Очистить метки", GpsTarget::clear));

	/** "event x z" of the events already marked. */
	private final Set<String> seen = new HashSet<>();
	private int ticks;

	public AutoEventGps() {
		super("auto_event_gps", "AutoEventGPS", "Автоматическая метка GPS на ивенты сервера (боссбары и чат)", Category.PLAYER);
	}

	private static List<MultiSelectSetting.Option> serverOptions() {
		List<MultiSelectSetting.Option> list = new ArrayList<>();
		for (EventPatterns.Server s : EventPatterns.SERVERS) {
			list.add(option(s.id(), s.label()));
		}
		return list;
	}

	/** Events of all servers (ids are unique across servers). */
	private static List<MultiSelectSetting.Option> eventOptions() {
		List<MultiSelectSetting.Option> list = new ArrayList<>();
		for (EventPatterns.Server s : EventPatterns.SERVERS) {
			for (EventPatterns.Event e : s.events()) {
				list.add(option(e.id(), e.label()));
			}
		}
		return list;
	}

	private static Set<String> allEvents() {
		Set<String> ids = new LinkedHashSet<>();
		for (EventPatterns.Server s : EventPatterns.SERVERS) {
			for (EventPatterns.Event e : s.events()) {
				ids.add(e.id());
			}
		}
		return ids;
	}

	@Override
	protected void onEnable() {
		seen.clear();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || ++ticks % 10 != 0) {
			return;
		}
		for (LerpingBossEvent bar : ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).elysium$events().values()) {
			check(bar.getName().getString());
		}
	}

	/** Called (mixin) for each chat message. */
	public static void onChat(Component message) {
		AutoEventGps m = ModuleManager.get().find(AutoEventGps.class);
		if (m != null && m.isEnabled()) {
			m.check(message.getString());
		}
	}

	/** Marks the event in {@code text}, if any and not marked yet; returns the match (or null). */
	public EventPatterns.Match check(String text) {
		EventPatterns.Match match = EventPatterns.find(EventPatterns.server(server.get()), events.get(), text);
		if (match == null || !seen.add(match.event().id() + " " + match.x() + " " + match.z())) {
			return match;
		}
		GpsTarget.set(match.x(), match.z(), match.event().label());
		Gps gps = ModuleManager.get().find(Gps.class);
		if (gps != null && !gps.isEnabled()) {
			gps.setEnabled(true);
		}
		double d = GpsTarget.distance();
		Notifications.alert(match.event().label(), match.x() + " " + match.z() + (d >= 0 ? " · " + Math.round(d) + " бл." : ""));
		return match;
	}
}
