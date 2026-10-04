package dev.elysium.visuals.test;

import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.Watermark;
import dev.elysium.visuals.client.module.impl.utils.FakePlayer;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Text crispness of the HUD: the target (a FakePlayer and several mobs) and the
 * other Interface blocks at GUI scales 1-4 and element scales 50-150 %.
 * Screenshots go to build/run/clientGameTest/screenshots; every shot logs the
 * target's rectangle ("[HudTextTest] ...") in screen pixels for close-ups.
 */
public class HudTextGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("summon minecraft:zombie ~3 ~ ~2 {NoAI:1b}");
			world.getServer().runCommand("summon minecraft:skeleton ~-3 ~ ~2 {NoAI:1b}");
			world.getServer().runCommand("summon minecraft:creeper ~3 ~ ~-2 {NoAI:1b}");
			world.getServer().runCommand("summon minecraft:pig ~-3 ~ ~-2 {NoAI:1b}");
			context.runOnClient(mc -> {
				// Only the Interface HUD, so nothing overlaps the target.
				for (Module m : ModuleManager.get().modules()) {
					if (!m.hudElements().isEmpty() && !(m instanceof Watermark)) {
						m.setEnabled(false);
					}
				}
				Watermark w = ModuleManager.get().find(Watermark.class);
				((MultiSelectSetting) setting(w, "elements")).set(Set.of("watermark", "target", "binds"));
				w.setBind(org.lwjgl.glfw.GLFW.GLFW_KEY_H);
				w.setEnabled(true);
				ModuleManager.get().find(FakePlayer.class).setEnabled(true);
			});
			context.waitTicks(40);

			// FakePlayer at every GUI scale and three element scales.
			for (int gui = 1; gui <= 4; gui++) {
				for (int scale : new int[]{50, 100, 150}) {
					shoot(context, "cards", gui, scale, "fake");
				}
			}
			// Every style on mobs at two GUI scales and two element scales.
			String[] mobs = {"zombie", "skeleton", "creeper", "pig"};
			String[] styles = {"minimal", "plates", "panels", "cards"};
			for (int i = 0; i < styles.length; i++) {
				for (int gui : new int[]{2, 3}) {
					for (int scale : new int[]{75, 100}) {
						shoot(context, styles[i], gui, scale, mobs[i]);
					}
				}
			}
			context.runOnClient(mc -> {
				ModuleManager.get().find(FakePlayer.class).setEnabled(false);
				Watermark w = ModuleManager.get().find(Watermark.class);
				((NumberSetting) setting(w, "scale_target")).set(100.0);
				w.setBind(Module.NO_KEY);
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		context.getInput().resizeWindow(854, 480);
	}

	private static void shoot(ClientGameTestContext context, String style, int gui, int scale, String who) {
		context.runOnClient(mc -> {
			Watermark w = ModuleManager.get().find(Watermark.class);
			((ModeSetting) setting(w, "style")).set(style);
			((NumberSetting) setting(w, "scale_target")).set((double) scale);
			mc.options.guiScale().set(gui);
			mc.resizeGui();
			LivingEntity target = find(mc, who);
			if (target == null) {
				throw new AssertionError("no " + who + " to target");
			}
			TargetTracker.onHit(target);
		});
		context.waitTicks(12);
		String name = "text-" + style + "-gui" + gui + "-" + scale + "-" + who;
		context.runOnClient(mc -> {
			HudElement t = ModuleManager.get().find(Watermark.class).hudElements().stream()
					.filter(e -> e.localId().equals("target")).findFirst().orElseThrow();
			int s = mc.getWindow().getGuiScale();
			System.out.printf("[HudTextTest] %s rect=%d,%d,%d,%d guiScale=%d%n", name,
					t.x() * s, t.y() * s, t.width() * s, t.height() * s, s);
		});
		context.takeScreenshot(name);
	}

	private static LivingEntity find(Minecraft mc, String who) {
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof LivingEntity living && e != mc.player) {
				boolean match = who.equals("fake") ? FakePlayer.isFake(e)
						: EntityType.getKey(e.getType()).getPath().equals(who);
				if (match) {
					return living;
				}
			}
		}
		return null;
	}

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}
}
