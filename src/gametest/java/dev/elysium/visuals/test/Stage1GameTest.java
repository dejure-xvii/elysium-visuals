package dev.elysium.visuals.test;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.elysium.visuals.client.compat.SodiumCompat;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.ChunkAnimator;
import dev.elysium.visuals.client.module.impl.render.FullBright;
import dev.elysium.visuals.client.module.impl.render.NoCameraClip;
import dev.elysium.visuals.client.module.impl.utils.Zoom;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Stage 1 modules: ChunkAnimator, FullBright, NoCameraClip, Zoom. Run with and
 * without Sodium ({@code -Psodium}); screenshots go to build/run/clientGameTest/screenshots/s1-*.png.
 */
public class Stage1GameTest implements FabricClientGameTest {
	private static final String TAG = SodiumCompat.INSTALLED ? "sodium-" : "vanilla-";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0 -60 0 0 0");
			context.waitTicks(40);

			chunkAnimator(context, world);
			fullBright(context, world);
			noCameraClip(context, world);
			zoom(context, world);

			context.runOnClient(mc -> {
				for (Class<? extends Module> c : List.of(ChunkAnimator.class, FullBright.class, NoCameraClip.class, Zoom.class)) {
					ModuleManager.get().find(c).setEnabled(false);
				}
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	private static void chunkAnimator(ClientGameTestContext context, TestSingleplayerContext world) {
		for (String mode : new String[]{"below", "above", "hybrid", "slide"}) {
			context.runOnClient(mc -> {
				ChunkAnimator m = ModuleManager.get().find(ChunkAnimator.class);
				mode(m, "mode").set(mode);
				mode(m, "easing").set(mode.equals("hybrid") ? "bounce" : "cubic");
				((NumberSetting) setting(m, "duration")).set(3000.0);
				m.setEnabled(true);
				// A section far away that has just appeared is moved; one that is long done isn't.
				Matrix4fc moved = ChunkAnimator.animate(new Matrix4f(), new BlockPos(160, 0, 160), Util.getMillis() - 50);
				Vector3f t = moved.getTranslation(new Vector3f());
				check(t.length() > 10, mode + ": fresh section offset " + t);
				Matrix4fc done = ChunkAnimator.animate(new Matrix4f(), new BlockPos(160, 0, 160), Util.getMillis() - 10_000);
				check(done.getTranslation(new Vector3f()).length() < 1e-4, mode + ": finished section in place");
				Matrix4fc near = ChunkAnimator.animate(new Matrix4f(), BlockPos.containing(mc.player.position()), Util.getMillis() - 50);
				check(near.getTranslation(new Vector3f()).length() < 1e-4, mode + ": section next to the player not animated");
			});
			// Wait for Sodium to rebuild its shaders with the new settings, then fly to fresh chunks.
			context.waitTicks(15);
			if (SodiumCompat.INSTALLED) {
				context.runOnClient(mc -> {
					String src = mc.getShaderManager().getShader(Identifier.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque"),
							ShaderType.VERTEX);
					check(src != null && src.contains("elysium_chunk_offset"), "Sodium terrain shader patched");
				});
			}
			int x = 2000 + (int) (Math.random() * 100000);
			world.getServer().runCommand("tp @a " + x + " -50 0 0 20");
			context.waitTicks(12);
			context.takeScreenshot("s1-" + TAG + "chunks-" + mode);
		}
		context.runOnClient(mc -> ModuleManager.get().find(ChunkAnimator.class).setEnabled(false));
		world.getServer().runCommand("tp @a 0 -60 0 0 0");
		context.waitTicks(40);
	}

	private static void fullBright(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("time set 18000");
		server.runCommand("fill -3 -61 17 3 -56 23 stone");
		server.runCommand("fill -2 -60 18 2 -57 22 air");
		server.runCommand("tp @a 0 -60 20 180 10");
		context.waitTicks(20);
		context.takeScreenshot("s1-" + TAG + "fullbright-off");
		for (String mode : new String[]{"night_vision", "gamma"}) {
			context.runOnClient(mc -> {
				FullBright m = ModuleManager.get().find(FullBright.class);
				mode(m, "mode").set(mode);
				m.setEnabled(true);
			});
			context.waitTicks(10);
			context.runOnClient(mc -> check(!mc.player.hasEffect(MobEffects.NIGHT_VISION), mode + ": no night vision effect on the player"));
			context.takeScreenshot("s1-" + TAG + "fullbright-" + mode);
		}
		context.runOnClient(mc -> ModuleManager.get().find(FullBright.class).setEnabled(false));
		server.runCommand("time set 6000");
	}

	private static void noCameraClip(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		// A wall right behind the player.
		server.runCommand("fill -3 -60 38 3 -55 38 stone");
		server.runCommand("tp @a 0 -60 40.5 0 10");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.runOnClient(mc -> {
			double d = mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition());
			check(d < 2, "vanilla: camera pulled in by the wall (" + d + ")");
		});
		context.runOnClient(mc -> ModuleManager.get().find(NoCameraClip.class).setEnabled(true));
		context.waitTicks(10);
		context.runOnClient(mc -> {
			double d = mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition());
			check(d > 3.5, "NoCameraClip: camera behind the wall (" + d + ")");
		});
		context.takeScreenshot("s1-" + TAG + "camera-third");
		// First person with the head in a block: no block texture over the screen.
		server.runCommand("setblock 0 -59 30 stone");
		server.runCommand("tp @a 0 -60 30 0 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
		context.takeScreenshot("s1-" + TAG + "camera-first-in-block");
		context.runOnClient(mc -> {
			NoCameraClip m = ModuleManager.get().find(NoCameraClip.class);
			((BooleanSetting) setting(m, "walk_bob")).set(false);
			((BooleanSetting) setting(m, "hurt_shake")).set(false);
			check(!NoCameraClip.walkBob() && !NoCameraClip.hurtShake(), "bob and shake switched off");
			m.setEnabled(false);
			check(NoCameraClip.walkBob() && NoCameraClip.hurtShake(), "module off: vanilla bob and shake");
		});
		server.runCommand("setblock 0 -59 30 air");
	}

	private static void zoom(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @a 0 -60 0 0 0");
		context.runOnClient(mc -> {
			Zoom m = ModuleManager.get().find(Zoom.class);
			((NumberSetting) setting(m, "amount")).set(4.0);
			((BooleanSetting) setting(m, "smooth")).set(false);
			m.setEnabled(true);
		});
		context.waitTicks(5);
		float[] normal = new float[1];
		context.runOnClient(mc -> normal[0] = mc.gameRenderer.mainCamera().getFov());
		context.getInput().holdKey(GLFW.GLFW_KEY_C);
		context.waitTicks(5);
		context.runOnClient(mc -> {
			float fov = mc.gameRenderer.mainCamera().getFov();
			check(Math.abs(fov - normal[0] / 4) < 0.5, "zoom x4: fov " + normal[0] + " -> " + fov);
			check(Math.abs(Zoom.sensitivity() - 0.25) < 0.01, "mouse slower while zoomed: " + Zoom.sensitivity());
		});
		context.takeScreenshot("s1-" + TAG + "zoom-4x");
		int slot = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		context.getInput().scroll(1);
		context.waitTicks(5);
		context.runOnClient(mc -> {
			float fov = mc.gameRenderer.mainCamera().getFov();
			check(fov < normal[0] / 4 - 0.5, "wheel zooms in further: " + fov);
			check(mc.player.getInventory().getSelectedSlot() == slot, "wheel didn't change the hotbar slot");
		});
		context.getInput().releaseKey(GLFW.GLFW_KEY_C);
		context.waitTicks(5);
		context.runOnClient(mc -> check(Math.abs(mc.gameRenderer.mainCamera().getFov() - normal[0]) < 0.5, "released: normal fov"));
	}

	// ---------------------------------------------------------------------

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("[Stage1Test] ok: " + what);
	}

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}

	private static ModeSetting mode(Module m, String id) {
		return (ModeSetting) setting(m, id);
	}
}
