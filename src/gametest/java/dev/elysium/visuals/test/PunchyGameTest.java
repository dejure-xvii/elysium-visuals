package dev.elysium.visuals.test;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.compat.PunchyCompat;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.SwingAnimation;
import dev.elysium.visuals.client.module.impl.render.ViewModel;
import dev.elysium.visuals.client.module.setting.ActionSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/**
 * SwingAnimation's Punchy style: Punchy runs only in that style, the own styles
 * and Punchy never animate at the same time, settings sync both ways, and the
 * game works without Punchy. Screenshots: build/run/clientGameTest/screenshots.
 * Run without Punchy: {@code gradlew runClientGameTest -PnoPunchy}.
 */
public class PunchyGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
			world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:shield");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(40);

			boolean installed = context.computeOnClient(mc -> PunchyCompat.available());
			System.out.println("[PunchyGameTest] Punchy installed: " + installed);
			if (installed) {
				withPunchy(context);
			} else {
				withoutPunchy(context);
			}
			context.runOnClient(mc -> ModuleManager.get().find(SwingAnimation.class).setEnabled(false));
			context.waitTicks(5);
		}
	}

	private static void withPunchy(ClientGameTestContext context) {
		// Module off: Punchy is switched off, hands are vanilla.
		check(context, "module off -> Punchy off", () -> !PunchyCompat.isEnabled());
		context.takeScreenshot("punchy-0-module-off");

		// Punchy style on.
		context.runOnClient(mc -> {
			SwingAnimation m = ModuleManager.get().find(SwingAnimation.class);
			mode(m).set("punchy");
			number(m, "speed").set(2.0);
			m.setEnabled(true);
		});
		context.waitTicks(2);
		check(context, "punchy style -> Punchy on", PunchyCompat::isEnabled);
		check(context, "own styles inactive while Punchy runs",
				() -> !SwingAnimation.apply(new PoseStack(), 0.5f, 1, HumanoidArm.RIGHT)
						&& SwingAnimation.swingDuration(6) == 6);
		context.waitTicks(30);
		context.takeScreenshot("punchy-1-on");
		context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(3);
		context.takeScreenshot("punchy-2-swing");
		context.waitTicks(20);

		// Settings: changes here reach Punchy, changes in Punchy come back.
		context.runOnClient(mc -> number(ModuleManager.get().find(SwingAnimation.class), "punchy_speed").set(3.0));
		context.waitTicks(2);
		check(context, "speed pushed to Punchy", () -> Math.abs(PunchyCompat.read().animationSpeed() - 3.0f) < 1e-4);
		context.runOnClient(mc -> {
			PunchyCompat.Values v = PunchyCompat.read();
			PunchyCompat.apply(new PunchyCompat.Values(4.0f, v.customWalk(), v.sprintSwing(), v.sprintIntensity(),
					v.itemPhysics(), v.particles()));
		});
		context.waitTicks(2);
		check(context, "speed taken back from Punchy",
				() -> Math.abs(number(ModuleManager.get().find(SwingAnimation.class), "punchy_speed").get() - 4.0) < 1e-4);
		context.runOnClient(mc -> number(ModuleManager.get().find(SwingAnimation.class), "punchy_speed").set(5.5));

		// ViewModel on top of Punchy.
		context.runOnClient(mc -> {
			Module vm = ModuleManager.get().find(ViewModel.class);
			number(vm, "right_x").set(0.3);
			number(vm, "right_scale").set(0.7);
			vm.setEnabled(true);
		});
		context.waitTicks(5);
		context.takeScreenshot("punchy-3-viewmodel");
		context.runOnClient(mc -> {
			Module vm = ModuleManager.get().find(ViewModel.class);
			vm.resetSettings();
			vm.setEnabled(false);
		});

		// "Open Punchy settings" button.
		context.runOnClient(mc -> action(ModuleManager.get().find(SwingAnimation.class), "punchy_settings").run());
		context.waitTicks(10);
		check(context, "Punchy settings screen opens",
				mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("PunchyConfigScreen"));
		context.takeScreenshot("punchy-4-settings-screen");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);

		// Own style: Punchy off, the own animation applies; hands re-equip smoothly.
		context.runOnClient(mc -> mode(ModuleManager.get().find(SwingAnimation.class)).set("chop"));
		context.waitTicks(1);
		check(context, "own style -> Punchy off", () -> !PunchyCompat.isEnabled()
				&& SwingAnimation.apply(new PoseStack(), 0.5f, 1, HumanoidArm.RIGHT)
				&& SwingAnimation.swingDuration(6) == 3);
		context.takeScreenshot("punchy-5-chop-transition");
		context.waitTicks(15);
		context.takeScreenshot("punchy-6-chop");
		context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(2);
		context.takeScreenshot("punchy-7-chop-swing");
		context.waitTicks(20);

		// Back to Punchy, then module off.
		context.runOnClient(mc -> mode(ModuleManager.get().find(SwingAnimation.class)).set("punchy"));
		context.waitTicks(2);
		check(context, "back to punchy style -> Punchy on", PunchyCompat::isEnabled);
		context.waitTicks(20);
		context.takeScreenshot("punchy-8-on-again");
		context.runOnClient(mc -> ModuleManager.get().find(SwingAnimation.class).setEnabled(false));
		context.waitTicks(2);
		check(context, "module off again -> Punchy off", () -> !PunchyCompat.isEnabled());
		context.waitTicks(15);
		context.takeScreenshot("punchy-9-off");
	}

	private static void withoutPunchy(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			SwingAnimation m = ModuleManager.get().find(SwingAnimation.class);
			mode(m).set("punchy");
			m.setEnabled(true);
		});
		context.waitTicks(5);
		check(context, "no Punchy: button asks to install it", () -> action(
				ModuleManager.get().find(SwingAnimation.class), "punchy_settings").label().contains("Установите Punchy"));
		check(context, "no Punchy: hands stay vanilla",
				() -> !SwingAnimation.apply(new PoseStack(), 0.5f, 1, HumanoidArm.RIGHT));
		context.takeScreenshot("nopunchy-1-hands");
		context.runOnClient(mc -> action(ModuleManager.get().find(SwingAnimation.class), "punchy_settings").run());
		context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(3);
		context.takeScreenshot("nopunchy-2-swing");
		context.runOnClient(mc -> mode(ModuleManager.get().find(SwingAnimation.class)).set("slant"));
		context.waitTicks(5);
		check(context, "no Punchy: own style works", () -> SwingAnimation.apply(new PoseStack(), 0.5f, 1, HumanoidArm.RIGHT));
	}

	// --- helpers ------------------------------------------------------------------

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}

	private static ModeSetting mode(Module m) {
		return (ModeSetting) setting(m, "mode");
	}

	private static NumberSetting number(Module m, String id) {
		return (NumberSetting) setting(m, id);
	}

	private static ActionSetting action(Module m, String id) {
		return (ActionSetting) setting(m, id);
	}

	private static void check(ClientGameTestContext context, String what, java.util.function.BooleanSupplier condition) {
		check(context, what, mc -> condition.getAsBoolean());
	}

	private static void check(ClientGameTestContext context, String what,
							  java.util.function.Predicate<net.minecraft.client.Minecraft> condition) {
		boolean ok = context.computeOnClient(condition::test);
		System.out.println("[PunchyGameTest] " + (ok ? "OK   " : "FAIL ") + what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
