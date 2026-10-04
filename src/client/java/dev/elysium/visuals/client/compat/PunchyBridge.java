package dev.elysium.visuals.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import punchy.client.config.PunchyConfigScreen;
import punchy.client.render.CameraAnimationState;
import punchy.client.render.UseAnimationSuppressor;
import punchy.client.state.HandEquipStateMachine;
import punchy.config.PunchyConfig;

/** Direct calls into Punchy. Only loaded through {@link PunchyCompat} when Punchy is installed. */
final class PunchyBridge {
	private PunchyBridge() {
	}

	static boolean isEnabled() {
		return PunchyConfig.isModEnabled();
	}

	static void setEnabled(boolean on) {
		PunchyConfig.Data data = PunchyConfig.snapshot();
		data.enableMod = on;
		PunchyConfig.applyRuntime(data);
		if (on) {
			// Punchy's state machines were paused while off: resync with what is in
			// the hands and bring the hand in with Punchy's equip animation.
			HandEquipStateMachine.requestRenderedSync();
			HandEquipStateMachine.triggerImmediateEquipCycle(Minecraft.getInstance(), InteractionHand.MAIN_HAND);
		} else {
			// Only Punchy's hand renderer updates these; reset them so nothing sticks.
			CameraAnimationState.clear();
			UseAnimationSuppressor.clear();
			UseAnimationSuppressor.force(false);
		}
	}

	static PunchyCompat.Values read() {
		PunchyConfig.Data d = PunchyConfig.snapshot();
		return new PunchyCompat.Values(d.animationSpeed, d.enableCustomWalk, d.enableSprintArmSwing,
				d.sprintArmSwingIntensity, !d.disableNativeItemPhysics, d.enableParticles);
	}

	static void apply(PunchyCompat.Values v) {
		PunchyConfig.Data d = PunchyConfig.snapshot();
		d.animationSpeed = v.animationSpeed();
		d.enableCustomWalk = v.customWalk();
		d.enableSprintArmSwing = v.sprintSwing();
		d.sprintArmSwingIntensity = v.sprintIntensity();
		d.disableNativeItemPhysics = !v.itemPhysics();
		d.enableParticles = v.particles();
		PunchyConfig.applyRuntime(d);
	}

	static void openSettings(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new PunchyConfigScreen(parent));
	}
}
