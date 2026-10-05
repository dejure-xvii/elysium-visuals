package dev.elysium.visuals.test;

import dev.elysium.visuals.client.compat.SodiumCompat;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.Shaders;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import dev.elysium.visuals.client.render.CloudRenderer3D;
import dev.elysium.visuals.client.render.IrisCompat;
import dev.elysium.visuals.client.render.ShadersRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

/**
 * Stage 4: Shaders — each effect alone, everything on each quality preset with
 * FPS, day / night / under water, wetness fading in and drying, the clouds
 * taken over from an old CustomSky config, and switching off for an Iris pack.
 * Screenshots s4-*.png.
 */
public class Stage4GameTest implements FabricClientGameTest {
	private static final String TAG = SodiumCompat.INSTALLED ? "sodium-" : "vanilla-";
	private static final List<String> EFFECTS = List.of("reflections", "sky", "clouds", "rays", "ao", "bloom", "tonemap",
			"exposure", "underwater", "dof", "chromatic", "sharpen", "wet");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 1500");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			// A small scene: a pool, pillars, a wall with a gap for the light rays, a glowstone lamp.
			server.runCommand("fill -12 -61 6 12 -61 30 stone_bricks");
			server.runCommand("fill -4 -61 10 4 -61 18 water");
			server.runCommand("fill -10 -60 22 10 -50 22 deepslate_bricks");
			server.runCommand("fill -1 -58 22 1 -52 22 air");
			server.runCommand("fill -8 -60 12 -8 -54 12 quartz_pillar");
			server.runCommand("fill 8 -60 12 8 -54 12 quartz_pillar");
			server.runCommand("setblock 6 -60 8 glowstone");
			server.runCommand("setblock -6 -60 8 sea_lantern");
			server.runCommand("tp @a 0 -59 2 0 12");
			context.waitTicks(40);
			context.runOnClient(mc -> {
				for (Module m : ModuleManager.get().modules()) {
					if (!m.hudElements().isEmpty()) {
						m.setEnabled(false);
					}
				}
				mc.player.getAbilities().flying = true;
			});
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.takeScreenshot("s4-" + TAG + "off");

			eachEffect(context, world);
			presets(context, world);
			wetness(context);
			underwaterAndNight(context, world);
			migration(context);
			iris(context);

			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.runOnClient(mc -> Shaders.get().setEnabled(false));
		}
	}

	private static void eachEffect(ClientGameTestContext context, TestSingleplayerContext world) {
		for (String effect : EFFECTS) {
			if (effect.equals("underwater")) {
				continue; // shot under water below
			}
			context.runOnClient(mc -> {
				Shaders m = Shaders.get();
				effects(Set.of(effect));
				mode(m, "quality").set("medium");
				m.setEnabled(true);
			});
			context.waitTicks(effect.equals("wet") ? 60 : effect.equals("exposure") ? 60 : effect.equals("clouds") ? 40 : 6);
			if (effect.equals("clouds")) {
				context.waitFor(mc -> CloudRenderer3D.active(), 20 * 30);
			}
			context.runOnClient(mc -> check(!ShadersRenderer.failed(), effect + ": no GPU error"));
			context.takeScreenshot("s4-" + TAG + "effect-" + effect);
		}
	}

	private static void presets(ClientGameTestContext context, TestSingleplayerContext world) {
		int prevLimit = context.computeOnClient(mc -> mc.options.framerateLimit().get());
		boolean prevVsync = context.computeOnClient(mc -> mc.options.enableVsync().get());
		context.runOnClient(mc -> {
			mc.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
			mc.options.enableVsync().set(false);
			Shaders.get().setEnabled(false);
		});
		context.waitTicks(60);
		int base = context.computeOnClient(mc -> mc.getFps());
		System.out.println("[Stage4Test] " + TAG + "fps off = " + base);
		for (String q : new String[]{"low", "medium", "high"}) {
			context.runOnClient(mc -> {
				Shaders m = Shaders.get();
				Set<String> all = new java.util.LinkedHashSet<>(EFFECTS);
				all.remove("dof");
				all.remove("chromatic");
				effects(all);
				mode(m, "quality").set(q);
				m.setEnabled(true);
			});
			context.waitTicks(80);
			int fps = context.computeOnClient(mc -> mc.getFps());
			System.out.println("[Stage4Test] " + TAG + "fps " + q + " (all but DoF/CA) = " + fps);
			context.runOnClient(mc -> check(!ShadersRenderer.failed(), q + ": no GPU error"));
			context.takeScreenshot("s4-" + TAG + "all-" + q);
		}
		context.runOnClient(mc -> {
			mc.options.framerateLimit().set(prevLimit);
			mc.options.enableVsync().set(prevVsync);
		});
	}

	private static void wetness(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			Shaders m = Shaders.get();
			m.setEnabled(false);
		});
		context.waitTicks(80); // dry
		context.runOnClient(mc -> {
			check(ShadersRenderer.wetness() == 0f, "dry before");
			effects(Set.of("wet", "reflections"));
			Shaders.get().setEnabled(true);
		});
		context.waitTicks(10);
		float half = context.computeOnClient(mc -> ShadersRenderer.wetness());
		check(half > 0.15f && half < 0.9f, "wetness fades in (0.5 s: " + half + ")");
		context.takeScreenshot("s4-" + TAG + "wet-fading-in");
		context.waitTicks(50);
		float full = context.computeOnClient(mc -> ShadersRenderer.wetness());
		check(full > 0.97f, "fully wet after ~3 s: " + full);
		context.takeScreenshot("s4-" + TAG + "wet-full");
		context.runOnClient(mc -> Shaders.get().setEnabled(false));
		context.waitTicks(10);
		float drying = context.computeOnClient(mc -> ShadersRenderer.wetness());
		check(drying > 0.1f && drying < 0.85f, "dries out smoothly after switching off (0.5 s: " + drying + ")");
		context.takeScreenshot("s4-" + TAG + "wet-drying");
		context.waitTicks(70);
		context.runOnClient(mc -> check(ShadersRenderer.wetness() == 0f, "dry again"));
	}

	private static void underwaterAndNight(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> {
			Shaders m = Shaders.get();
			effects(Set.of("underwater", "ao", "bloom", "tonemap", "rays", "sky", "reflections"));
			m.setEnabled(true);
		});
		world.getServer().runCommand("fill -4 -62 10 4 -62 18 sand");
		world.getServer().runCommand("tp @a 0 -61.5 14 0 0");
		context.waitTicks(20);
		context.takeScreenshot("s4-" + TAG + "underwater");
		world.getServer().runCommand("tp @a 0 -59 2 0 12");
		world.getServer().runCommand("time set 18000");
		context.waitTicks(20);
		context.takeScreenshot("s4-" + TAG + "night");
		// Morning sun low in the east, behind a wall with a gap: light rays through it.
		world.getServer().runCommand("time set 1200");
		world.getServer().runCommand("fill 16 -60 -10 16 -40 14 deepslate_bricks");
		world.getServer().runCommand("fill 16 -54 0 16 -44 4 air");
		world.getServer().runCommand("tp @a 0 -59 2 -90 -22");
		context.waitTicks(20);
		context.takeScreenshot("s4-" + TAG + "rays-through-gap");
		// Blocky clouds.
		context.runOnClient(mc -> {
			effects(Set.of("clouds"));
			mode(Shaders.get(), "cloud_style").set("blocky");
		});
		world.getServer().runCommand("time set 6000");
		world.getServer().runCommand("tp @a 0 -59 2 -90 -30");
		context.waitTicks(20);
		context.takeScreenshot("s4-" + TAG + "clouds-blocky");
		context.runOnClient(mc -> mode(Shaders.get(), "cloud_style").set("natural"));
		world.getServer().runCommand("fill 16 -60 -10 16 -40 14 air");
		world.getServer().runCommand("time set 1500");
		world.getServer().runCommand("tp @a 0 -59 2 0 12");
		context.runOnClient(mc -> check(!ShadersRenderer.failed(), "no GPU error under water / at night"));
	}

	/** An old config with CustomSky's 3D clouds hands them to Shaders. */
	private static void migration(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			Shaders m = Shaders.get();
			m.setEnabled(false);
			effects(Set.of("ao"));
			CustomSky sky = ModuleManager.get().find(CustomSky.class);
			((BooleanSetting) setting(sky, "clouds_3d")).set(true);
			((NumberSetting) setting(sky, "cloud_height")).set(240.0);
		});
		context.waitTicks(3);
		context.runOnClient(mc -> {
			Shaders m = Shaders.get();
			check(m.isEnabled() && m.selected("clouds"), "clouds carried over to Shaders");
			check(m.cloudHeight() == 240.0, "cloud height carried over");
			check(!((BooleanSetting) setting(ModuleManager.get().find(CustomSky.class), "clouds_3d")).isOn(), "old flag cleared");
			check(!setting(ModuleManager.get().find(CustomSky.class), "clouds_3d").isVisible(), "old cloud settings hidden in CustomSky");
		});
	}

	private static void iris(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			Shaders.get().setEnabled(true);
			IrisCompat.overrideForTests(true);
		});
		context.waitTicks(3);
		context.runOnClient(mc -> {
			check(!Shaders.get().isEnabled(), "an Iris shader pack switches Shaders off");
			check(!ShadersRenderer.active() && !CloudRenderer3D.active(), "no passes with a shader pack");
			IrisCompat.overrideForTests(null);
		});
		context.takeScreenshot("s4-" + TAG + "iris-off");
	}

	// ---------------------------------------------------------------------

	private static void effects(Set<String> ids) {
		((MultiSelectSetting) setting(Shaders.get(), "effects")).set(new java.util.LinkedHashSet<>(ids));
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("[Stage4Test] ok: " + what);
	}

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}

	private static ModeSetting mode(Module m, String id) {
		return (ModeSetting) setting(m, id);
	}
}
