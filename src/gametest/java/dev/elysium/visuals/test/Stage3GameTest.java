package dev.elysium.visuals.test;

import dev.elysium.visuals.client.command.CommandManager;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.player.PvPHelper;
import dev.elysium.visuals.client.module.impl.utils.BowOptimizer;
import dev.elysium.visuals.client.module.impl.utils.CrystalOptimizer;
import dev.elysium.visuals.client.module.impl.utils.FakePlayer;
import dev.elysium.visuals.client.module.impl.utils.RegionHelper;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import dev.elysium.visuals.client.module.setting.StringSetting;
import dev.elysium.visuals.client.region.Regions;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/** Stage 3: BowOptimizer, CrystalOptimizer, PvPHelper, RegionHelper; screenshots s3-*.png. */
public class Stage3GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("difficulty peaceful");
			server.runCommand("tp @a 0 -60 0 0 0");
			context.waitTicks(40);

			bow(context, world);
			crystal(context, world);
			pvpHelper(context, world);
			regions(context, world);

			context.runOnClient(mc -> {
				for (Class<? extends Module> c : List.of(BowOptimizer.class, CrystalOptimizer.class, PvPHelper.class,
						RegionHelper.class, FakePlayer.class)) {
					ModuleManager.get().find(c).setEnabled(false);
				}
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	private static void bow(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with bow");
		server.runCommand("give @a arrow 64");
		server.runCommand("kill @e[type=arrow]");
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			BowOptimizer m = ModuleManager.get().find(BowOptimizer.class);
			((NumberSetting) setting(m, "ticks")).set(5.0);
			m.setEnabled(true);
		});
		context.waitTicks(5);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(40);
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(5);
		int arrows = context.computeOnClient(mc -> mc.level.getEntitiesOfClass(Arrow.class, new AABB(mc.player.blockPosition()).inflate(80)).size());
		check(arrows >= 3, "bow released by itself while held: " + arrows + " arrows in 40 ticks");
		context.runOnClient(mc -> ModuleManager.get().find(BowOptimizer.class).setEnabled(false));
		server.runCommand("kill @e[type=arrow]");
		server.runCommand("clear @a");
	}

	private static void crystal(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance 30 255 true");
		server.runCommand("setblock 0 -61 3 obsidian");
		for (boolean on : new boolean[]{false, true}) {
			server.runCommand("kill @e[type=end_crystal]");
			server.runCommand("summon end_crystal 0.5 -60 3.5 {ShowBottom:0b}");
			context.waitTicks(10);
			context.runOnClient(mc -> {
				ModuleManager.get().find(CrystalOptimizer.class).setEnabled(on);
				EndCrystal c = crystal(mc);
				check(c != null, "crystal on the client");
				mc.gameMode.attack(mc.player, c);
				boolean gone = crystal(mc) == null;
				check(gone == on, (on ? "with" : "without") + " CrystalOptimizer the crystal is " + (gone ? "gone" : "still there")
						+ " right after the hit");
			});
			context.waitTicks(10);
			context.runOnClient(mc -> check(crystal(mc) == null, "server removed the crystal too"));
		}
		server.runCommand("effect clear @a");
		server.runCommand("gamemode creative @a");
		context.runOnClient(mc -> ModuleManager.get().find(CrystalOptimizer.class).setEnabled(false));
	}

	private static EndCrystal crystal(net.minecraft.client.Minecraft mc) {
		List<EndCrystal> list = mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(mc.player.blockPosition()).inflate(10));
		return list.isEmpty() ? null : list.getFirst();
	}

	private static void pvpHelper(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("tp @a 0 -60 0 0 10");
		server.runCommand("item replace entity @a hotbar.2 with totem_of_undying");
		server.runCommand("item replace entity @a hotbar.4 with ender_pearl 16");
		server.runCommand("item replace entity @a hotbar.6 with enchanted_golden_apple 3");
		server.runCommand("item replace entity @a hotbar.7 with golden_apple 3");
		context.runOnClient(mc -> {
			PvPHelper m = ModuleManager.get().find(PvPHelper.class);
			m.setEnabled(true);
			// Black list through the command.
			CommandManager.get().handle(".blacklist add Griefer");
			check(PvPHelper.hides(Component.literal("<Griefer> купите мой шоп")), "black-listed <Nick> hidden");
			check(PvPHelper.hides(Component.literal("[VIP] Griefer » привет")), "black-listed with a rank hidden");
			check(!PvPHelper.hides(Component.literal("<Steve> привет")), "others shown");
			check(!PvPHelper.hides(Component.literal("Griefer зашёл на сервер")), "server messages about them shown");
			CommandManager.get().handle(".blacklist remove Griefer");
			check(!PvPHelper.hides(Component.literal("<Griefer> снова я")), "removed from the black list");
			// Outlines: the fake player is the target.
			((MultiSelectSetting) setting(m, "glow")).set(Set.of("target"));
			((StringSetting) setting(m, "target")).set(mc.player.getGameProfile().name());
		});
		context.waitTicks(5);
		context.runOnClient(mc -> ModuleManager.get().find(FakePlayer.class).setEnabled(true));
		context.waitTicks(10);
		context.runOnClient(mc -> {
			Entity fake = mc.level.getEntity(-0x454C59);
			check(fake != null && PvPHelper.outlineColor(fake) != 0 && mc.shouldEntityAppearGlowing(fake), "target outlined");
		});
		context.takeScreenshot("s3-pvp-target-visible");
		context.runOnClient(mc -> {
			ModuleManager.get().find(PvPHelper.class).setEnabled(false);
			Entity fake = mc.level.getEntity(-0x454C59);
			System.out.println("[Stage3Test] fake at " + fake.position() + ", player at " + mc.player.position() + " yaw " + mc.player.getYRot());
		});
		context.waitTicks(3);
		context.takeScreenshot("s3-pvp-control-off");
		context.runOnClient(mc -> ModuleManager.get().find(PvPHelper.class).setEnabled(true));
		context.runOnClient(mc -> check(PvPHelper.outlineHookRan, "outline depth hook runs"));
		// A wall between you and the target: no outline through it.
		server.runCommand("fill -2 -60 1 2 -57 1 glass");
		server.runCommand("fill -1 -60 1 1 -57 1 stone");
		context.waitTicks(10);
		context.takeScreenshot("s3-pvp-target-behind-wall");
		server.runCommand("fill -2 -60 1 2 -57 1 air");
		context.runOnClient(mc -> ModuleManager.get().find(FakePlayer.class).setEnabled(false));
	}

	private static void regions(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @a 0 -60 -8 0 20");
		context.runOnClient(mc -> {
			RegionHelper m = ModuleManager.get().find(RegionHelper.class);
			m.setEnabled(true);
			((NumberSetting) setting(m, "fill")).set(20.0);
			Regions.reset();
			RegionHelper.onChat(Component.literal("§eRegion: §fhome §7(type=cuboid, priority=0)"));
			RegionHelper.onChat(Component.literal("§eBounds: §f(2, -61, 4) -> (12, -55, 14)"));
			check(Regions.regions().size() == 1 && Regions.regions().getFirst().name().equals("home"), "region from /rg info: " + Regions.regions());
			RegionHelper.onChat(Component.literal("Регион: shop"));
			RegionHelper.onChat(Component.literal("Границы: (-12, -61, 4) -> (-5, -50, 9)"));
			check(Regions.regions().size() == 2, "Russian /rg info too");
			// Selection over CUI.
			Regions.onCui("s|cuboid");
			Regions.onCui("p|0|-3|-61|20|0");
			Regions.onCui("p|1|3|-59|24|105");
			int[] s = Regions.selectionSize();
			check(s != null && s[0] == 7 && s[1] == 3 && s[2] == 5, "CUI selection 7x3x5");
		});
		context.waitTicks(5);
		context.takeScreenshot("s3-regions");
		context.runOnClient(mc -> {
			Regions.clearSelection();
			// Without CUI: WorldEdit's chat replies.
			RegionHelper.onChat(Component.literal("First position set to (1, -60, 1)."));
			RegionHelper.onChat(Component.literal("Вторая позиция установлена в (4, -58, 2) (12)."));
			int[] s = Regions.selectionSize();
			check(s != null && s[0] == 4 && s[1] == 3 && s[2] == 2, "selection from chat 4x3x2");
			RegionHelper m = ModuleManager.get().find(RegionHelper.class);
			((BooleanSetting) setting(m, "through_walls")).set(true);
		});
		context.waitTicks(5);
		context.takeScreenshot("s3-regions-xray");
		context.runOnClient(mc -> {
			Regions.reset();
			check(Regions.regions().isEmpty() && Regions.selectionSize() == null, "cleared");
		});
	}

	// ---------------------------------------------------------------------

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("[Stage3Test] ok: " + what);
	}

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}
}
