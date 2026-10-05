package dev.elysium.visuals.test;

import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.Shaders;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import dev.elysium.visuals.client.render.CloudRenderer3D;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The volumetric clouds (a Shaders effect, under CustomSky's summer sky): day, sunset, night, rain and thunder, from
 * below, inside the layer and above, at low and high quality, in another sky
 * mode, and vanilla clouds back when they are off. Screenshots:
 * build/run/clientGameTest/screenshots/clouds-*.png
 */
public class CloudsGameTest implements FabricClientGameTest {
	private static final int HEIGHT = 192;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runCommand("gamerule doWeatherCycle false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamemode creative @a");
			context.runOnClient(mc -> {
				for (Module m : ModuleManager.get().modules()) {
					if (!m.hudElements().isEmpty()) {
						m.setEnabled(false);
					}
				}
				CustomSky sky = ModuleManager.get().find(CustomSky.class);
				mode(sky, "mode").set("summer");
				sky.setEnabled(true);
				clouds(true);
				Shaders.get().setEnabled(true);
			});
			context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_F1); // clean shots, no HUD
			// The noise is generated in the background on first use.
			context.waitFor(mc -> CloudRenderer3D.active(), 20 * 30);

			look(context, world, 64, -25);
			shoot(context, world, "day", 6000, "clear");
			shoot(context, world, "morning", 1000, "clear");
			shoot(context, world, "golden", 11500, "clear");
			shoot(context, world, "sunset", 12300, "clear");
			shoot(context, world, "night", 18000, "clear");
			shoot(context, world, "rain", 6000, "rain");
			shoot(context, world, "thunder", 6000, "thunder");
			world.getServer().runCommand("weather clear");

			// Straight up from under the clouds.
			look(context, world, 64, -70);
			shoot(context, world, "below", 6000, "clear");
			// Flying up through the layer: under the base, entering, middle, near the top, above.
			int[] heights = {HEIGHT - 10, HEIGHT + 12, HEIGHT + 40, HEIGHT + 75, HEIGHT + 130};
			for (int i = 0; i < heights.length; i++) {
				look(context, world, heights[i], -8);
				shoot(context, world, "fly-" + i + "-y" + heights[i], 6000, "clear");
			}
			// Inside a dense cloud: full coverage, middle of the layer -> soft fog all around.
			context.runOnClient(mc -> ((dev.elysium.visuals.client.module.setting.NumberSetting)
					setting(Shaders.get(), "cloud_coverage")).set(1.0));
			look(context, world, HEIGHT + 25, 0);
			shoot(context, world, "fly-inside-dense", 6000, "clear");
			context.runOnClient(mc -> ((dev.elysium.visuals.client.module.setting.NumberSetting)
					setting(Shaders.get(), "cloud_coverage")).set(0.5));
			// Above the clouds, looking down.
			look(context, world, HEIGHT + 220, 35);
			shoot(context, world, "above", 6000, "clear");
			shoot(context, world, "above-sunset", 12300, "clear");

			look(context, world, 64, -25);
			for (String quality : new String[]{"low", "high"}) {
				context.runOnClient(mc -> mode(Shaders.get(), "quality").set(quality));
				shoot(context, world, "quality-" + quality, 6000, "clear");
			}
			context.runOnClient(mc -> mode(Shaders.get(), "quality").set("medium"));

			// Cost: FPS on the same view with clouds off and at each quality (no frame cap, no vsync).
			int prevLimit = context.computeOnClient(mc -> mc.options.framerateLimit().get());
			boolean prevVsync = context.computeOnClient(mc -> mc.options.enableVsync().get());
			context.runOnClient(mc -> {
				mc.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
				mc.options.enableVsync().set(false);
			});
			look(context, world, 64, -25);
			world.getServer().runCommand("time set 6000");
			for (String q : new String[]{"off", "low", "medium", "high"}) {
				context.runOnClient(mc -> {
					clouds(!q.equals("off"));
					if (!q.equals("off")) {
						mode(Shaders.get(), "quality").set(q);
					}
				});
				context.waitTicks(60);
				int fps = context.computeOnClient(mc -> mc.getFps());
				System.out.println("[CloudsGameTest] fps " + q + " = " + fps);
			}
			context.runOnClient(mc -> {
				clouds(true);
				mode(Shaders.get(), "quality").set("medium");
			});
			context.runOnClient(mc -> {
				mc.options.framerateLimit().set(prevLimit);
				mc.options.enableVsync().set(prevVsync);
			});

			// Any sky mode.
			context.runOnClient(mc -> mode(ModuleManager.get().find(CustomSky.class), "mode").set("aurora"));
			shoot(context, world, "aurora", 6000, "clear");
			context.runOnClient(mc -> mode(ModuleManager.get().find(CustomSky.class), "mode").set("summer"));

			// Off: vanilla clouds again.
			context.runOnClient(mc -> clouds(false));
			context.waitTicks(5);
			if (context.computeOnClient(mc -> CloudRenderer3D.active())) {
				throw new AssertionError("3D clouds must stop when switched off");
			}
			shoot(context, world, "off-vanilla", 6000, "clear");

			context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_F1);
			context.runOnClient(mc -> {
				ModuleManager.get().find(CustomSky.class).setEnabled(false);
				Shaders.get().setEnabled(false);
			});
		}
		context.getInput().resizeWindow(854, 480);
	}

	private static void look(ClientGameTestContext context, TestSingleplayerContext world, int y, int pitch) {
		world.getServer().runCommand("tp @a 0 " + y + " 0 -90 " + pitch);
		context.runOnClient(mc -> {
			mc.player.getAbilities().flying = true;
			mc.player.setXRot(pitch);
			mc.player.setYRot(-90);
		});
		context.waitTicks(10);
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, int time, String weather) {
		world.getServer().runCommand("time set " + time);
		world.getServer().runCommand("weather " + weather);
		// Rain/thunder fade in over a few seconds.
		context.waitTicks(weather.equals("clear") ? 20 : 140);
		boolean on = context.computeOnClient(mc -> CloudRenderer3D.active());
		System.out.println("[CloudsGameTest] " + name + " 3d=" + on);
		context.takeScreenshot("clouds-" + name);
	}

	/** Only the clouds among the Shaders effects, so the shots compare with earlier ones. */
	private static void clouds(boolean on) {
		((MultiSelectSetting) setting(Shaders.get(), "effects")).set(on ? java.util.Set.of("clouds") : java.util.Set.of());
	}

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}

	private static ModeSetting mode(Module m, String id) {
		return (ModeSetting) setting(m, id);
	}
}
