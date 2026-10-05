package dev.elysium.visuals.test;

import dev.elysium.visuals.client.command.CommandManager;
import dev.elysium.visuals.client.gps.EventPatterns;
import dev.elysium.visuals.client.gps.GpsTarget;
import dev.elysium.visuals.client.mixin.BossHealthOverlayAccessor;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.player.AutoEventGps;
import dev.elysium.visuals.client.module.impl.utils.Gps;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

import java.util.Set;
import java.util.UUID;

/** Stage 2: the .gps command, the GPS plate and pillar, AutoEventGPS; screenshots s2-*.png. */
public class Stage2GameTest implements FabricClientGameTest {
	private static final Set<String> ALL = Set.of("airdrop", "talisman", "trader");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		patterns();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0 -60 0 0 0");
			context.waitTicks(40);

			// .gps x z: mark, plate, pillar.
			context.runOnClient(mc -> {
				check(ModuleManager.get().find(Gps.class).isEnabled(), "GPS on by default");
				CommandManager.get().handle(".gps 40 60");
				GpsTarget.Mark m = GpsTarget.get();
				check(m != null && m.x() == 40 && m.z() == 60, "mark set: " + m);
				check(Math.abs(GpsTarget.distance() - Math.hypot(40, 60)) < 1.5, "distance " + GpsTarget.distance());
			});
			context.waitTicks(10);
			context.takeScreenshot("s2-gps-ahead");
			world.getServer().runCommand("tp @a 0 -60 0 180 0");
			context.waitTicks(10);
			context.takeScreenshot("s2-gps-behind");
			context.runOnClient(mc -> {
				CommandManager.get().handle(".gps off");
				check(GpsTarget.get() == null, ".gps off removes the mark");
				CommandManager.get().handle(".gps abc 5");
				check(GpsTarget.get() == null, "bad coordinates rejected");
				CommandManager.get().handle(".gps 2 2");
			});
			// Walking up to it: the mark goes away.
			context.waitTicks(5);
			context.runOnClient(mc -> check(GpsTarget.get() == null, "within 5 blocks the mark is removed"));
			context.takeScreenshot("s2-gps-arrived");

			// AutoEventGPS: a boss bar with an airdrop.
			context.runOnClient(mc -> {
				ModuleManager.get().find(AutoEventGps.class).setEnabled(true);
				LerpingBossEvent bar = new LerpingBossEvent(UUID.randomUUID(),
						Component.literal("§cРедкий Аирдроп: §f123 -1230 §7Босс не убит"), 1f,
						BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, false, false, false);
				((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).elysium$events().put(bar.getId(), bar);
			});
			context.waitTicks(15);
			context.runOnClient(mc -> {
				GpsTarget.Mark m = GpsTarget.get();
				check(m != null && m.x() == 123 && m.z() == -1230 && m.label().equals("Аирдроп"), "airdrop from the boss bar: " + m);
			});
			context.takeScreenshot("s2-auto-airdrop");
			// Cleared marks are not set again by the same boss bar.
			context.runOnClient(mc -> GpsTarget.clear());
			context.waitTicks(15);
			context.runOnClient(mc -> {
				check(GpsTarget.get() == null, "same event isn't marked twice");
				((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).elysium$events().clear();
				// From chat: a trader with x y z.
				AutoEventGps.onChat(Component.literal("[Ивент] Появился Тайный торговец на x: 500, y: 70, z: -200!"));
				GpsTarget.Mark m = GpsTarget.get();
				check(m != null && m.x() == 500 && m.z() == -200 && m.label().equals("Тайный торговец"), "trader from chat: " + m);
				GpsTarget.clear();
				ModuleManager.get().find(AutoEventGps.class).setEnabled(false);
			});
		}
	}

	private static void patterns() {
		EventPatterns.Server rw = EventPatterns.server("reallyworld");
		match(rw, "Редкий Аирдроп: 123 -1230 Босс не убит", "airdrop", 123, -1230);
		match(rw, "§6Аирдроп §fx: -50 y: 64 z: 900", "airdrop", -50, 900);
		match(rw, "Талисман появился: 10, 20", "talisman", 10, 20);
		match(rw, "Тайный торговец 7 65 8", "trader", 7, 8);
		check(EventPatterns.find(rw, ALL, "Аирдроп скоро будет!") == null, "no coordinates: no match");
		check(EventPatterns.find(rw, Set.of("talisman"), "Аирдроп 1 2") == null, "disabled event ignored");
		check(EventPatterns.find(rw, ALL, "Просто сообщение 100 200") == null, "no keyword: no match");
	}

	private static void match(EventPatterns.Server s, String text, String id, int x, int z) {
		EventPatterns.Match m = EventPatterns.find(s, ALL, text);
		check(m != null && m.event().id().equals(id) && m.x() == x && m.z() == z, "pattern '" + text + "' -> " + m);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("[Stage2Test] ok: " + what);
	}
}
