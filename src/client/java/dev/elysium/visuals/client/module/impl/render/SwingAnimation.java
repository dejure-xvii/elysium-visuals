package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elysium.visuals.client.compat.PunchyCompat;
import dev.elysium.visuals.client.mixin.ItemInHandRendererAccessor;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ActionSetting;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * First-person swing styles. Purely visual: it changes how your own hand is
 * drawn (and how long the local animation lasts), not what is sent to the server.
 * <p>
 * The "punchy" style hands the first-person arms over to the Punchy! mod (if
 * installed). Exactly one animator is active at a time: Punchy runs only in
 * that style while the module is on; otherwise it is switched off and the
 * hands are vanilla or use the selected style.
 */
public class SwingAnimation extends Module {
	/** Modes that use the "angle" setting. */
	private static final Set<String> USES_ANGLE = Set.of("slant", "chop", "overhead", "wave");
	private static final String PUNCHY = "punchy";
	private static final String NAME = "SwingAnimation";

	private final ModeSetting mode = add(new ModeSetting("mode", "Стиль",
			List.of(
					option("slant", "Наклонный"),
					option("chop", "Рубящий"),
					option("spin", "Вращение"),
					option("fade", "Fade"),
					option("stab", "Выпад"),
					option("overhead", "Сверху"),
					option("block", "Блок-удар"),
					option("wave", "Волна"),
					option(PUNCHY, "Punchy")),
			"slant"));
	private final NumberSetting strength = add(new NumberSetting("strength", "Сила взмаха", 1, 0.2, 2, 0.05, "x"))
			.visibleWhen(this::ownStyle);
	private final NumberSetting angle = add(new NumberSetting("angle", "Угол", 60, 10, 140, 1, "°"))
			.visibleWhen(() -> USES_ANGLE.contains(mode.get()));
	private final NumberSetting speed = add(new NumberSetting("speed", "Скорость взмаха", 1, 0.5, 2, 0.05, "x"))
			.visibleWhen(this::ownStyle);
	private final NumberSetting offsetX = add(new NumberSetting("offset_x", "Смещение X", 0, -1, 1, 0.01))
			.visibleWhen(this::ownStyle);
	private final NumberSetting offsetY = add(new NumberSetting("offset_y", "Смещение Y", 0, -1, 1, 0.01))
			.visibleWhen(this::ownStyle);
	private final NumberSetting offsetZ = add(new NumberSetting("offset_z", "Смещение Z", 0, -1, 1, 0.01))
			.visibleWhen(this::ownStyle);

	// Punchy style: its main options (applied to Punchy for this session), the rest in Punchy's own screen.
	private final NumberSetting punchySpeed = add(new NumberSetting("punchy_speed", "Скорость анимаций", 5.5, 0.5, 7, 0.1))
			.visibleWhen(this::punchyControls);
	private final BooleanSetting punchyWalk = add(new BooleanSetting("punchy_walk", "Анимация ходьбы", true))
			.visibleWhen(this::punchyControls);
	private final BooleanSetting punchySprint = add(new BooleanSetting("punchy_sprint", "Руки при беге", true))
			.visibleWhen(this::punchyControls);
	private final NumberSetting punchySprintPower = add(new NumberSetting("punchy_sprint_power", "Сила рук при беге", 1, -1, 1, 0.05))
			.visibleWhen(() -> punchyControls() && punchySprint.isOn());
	private final BooleanSetting punchyPhysics = add(new BooleanSetting("punchy_physics", "Физика предметов в руке", true))
			.visibleWhen(this::punchyControls);
	private final BooleanSetting punchyParticles = add(new BooleanSetting("punchy_particles", "Частицы", true))
			.visibleWhen(this::punchyControls);
	private final ActionSetting punchyScreen = add(new ActionSetting("punchy_settings", SwingAnimation::punchyButtonLabel,
			SwingAnimation::openPunchySettings))
			.visibleWhen(() -> mode.is(PUNCHY));

	/** Hand style drawn last tick: "vanilla", "punchy" or one of the own modes. */
	private static String lastStyle;
	private static boolean warnedMissing;
	/** Punchy values last applied to / taken from Punchy; null while the Punchy style is inactive. */
	private static PunchyCompat.Values synced;

	public SwingAnimation() {
		super("swing_animation", "SwingAnimation", "Разные стили взмаха оружием от первого лица", Category.RENDER);
	}

	private boolean ownStyle() {
		return !mode.is(PUNCHY);
	}

	private boolean punchyControls() {
		return mode.is(PUNCHY) && PunchyCompat.available();
	}

	/** Enabled and using one of its own styles (not Punchy). */
	private static SwingAnimation active() {
		SwingAnimation m = ModuleManager.get().find(SwingAnimation.class);
		return m != null && m.isEnabled() && m.ownStyle() ? m : null;
	}

	/** Swing duration for the local player's animation, scaled by the speed setting. */
	public static int swingDuration(int vanilla) {
		SwingAnimation m = active();
		return m == null ? vanilla : Math.max(2, Math.round(vanilla / m.speed.floatValue()));
	}

	// --- Punchy ---------------------------------------------------------------

	private static String punchyButtonLabel() {
		if (PunchyCompat.available()) {
			return "Открыть настройки Punchy";
		}
		return PunchyCompat.incompatible() ? "Эта версия Punchy не поддерживается" : "Установите Punchy через лаунчер";
	}

	private static void openPunchySettings() {
		Minecraft mc = Minecraft.getInstance();
		if (!PunchyCompat.openSettings(mc.gui.screen())) {
			warnMissing();
		}
	}

	private static void warnMissing() {
		Notifications.alert(NAME, PunchyCompat.incompatible()
				? "Эта версия Punchy не поддерживается, обновите её через лаунчер"
				: "Установите Punchy через лаунчер");
	}

	private PunchyCompat.Values punchyValues() {
		return new PunchyCompat.Values(punchySpeed.floatValue(), punchyWalk.isOn(), punchySprint.isOn(),
				punchySprintPower.floatValue(), punchyPhysics.isOn(), punchyParticles.isOn());
	}

	private void adopt(PunchyCompat.Values v) {
		punchySpeed.set((double) v.animationSpeed());
		punchyWalk.set(v.customWalk());
		punchySprint.set(v.sprintSwing());
		punchySprintPower.set((double) v.sprintIntensity());
		punchyPhysics.set(v.itemPhysics());
		punchyParticles.set(v.particles());
	}

	/**
	 * Two-way sync of the Punchy options: changes made here are applied to Punchy,
	 * changes made in Punchy's own screen are taken over here.
	 */
	private void syncPunchySettings() {
		PunchyCompat.Values ours = punchyValues();
		if (synced != null && ours.equals(synced)) {
			PunchyCompat.Values theirs = PunchyCompat.read();
			if (theirs == null || theirs.equals(synced)) {
				return;
			}
			adopt(theirs);
			ours = punchyValues();
		}
		PunchyCompat.apply(ours);
		synced = ours;
	}

	private static String style(SwingAnimation m) {
		if (m == null || !m.isEnabled()) {
			return "vanilla";
		}
		if (m.mode.is(PUNCHY)) {
			return PunchyCompat.available() ? PUNCHY : "vanilla";
		}
		return m.mode.get();
	}

	/**
	 * Runs every client tick, also while the module is off: keeps Punchy on only
	 * in the Punchy style, and smooths style changes by letting the vanilla hands
	 * re-equip (Punchy plays its own equip animation when it takes over).
	 */
	public static void syncHands(Minecraft mc) {
		SwingAnimation m = ModuleManager.get().find(SwingAnimation.class);
		boolean wantPunchy = m != null && m.isEnabled() && m.mode.is(PUNCHY);

		if (PunchyCompat.available() && PunchyCompat.isEnabled() != wantPunchy) {
			PunchyCompat.setEnabled(wantPunchy);
		}
		if (wantPunchy && PunchyCompat.available()) {
			m.syncPunchySettings();
		} else {
			synced = null;
		}

		if (wantPunchy && !PunchyCompat.available()) {
			if (!warnedMissing && mc.player != null) {
				warnedMissing = true;
				warnMissing();
			}
		} else {
			warnedMissing = false;
		}

		String style = style(m);
		if (lastStyle != null && !style.equals(lastStyle) && !style.equals(PUNCHY)) {
			lowerHands(mc);
		}
		lastStyle = style;
	}

	/** Drops both vanilla hands so they rise into the new style like after an item switch. */
	private static void lowerHands(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		ItemInHandRendererAccessor hands = (ItemInHandRendererAccessor) mc.gameRenderer.itemInHandRenderer;
		hands.elysium$setMainHandHeight(0);
		hands.elysium$setOMainHandHeight(0);
		hands.elysium$setOffHandHeight(0);
		hands.elysium$setOOffHandHeight(0);
	}

	// --- Own styles -----------------------------------------------------------

	/**
	 * Replaces the vanilla swing transform.
	 *
	 * @param attack swing progress 0..1
	 * @return false to let vanilla draw its own swing
	 */
	public static boolean apply(PoseStack pose, float attack, int invert, HumanoidArm arm) {
		SwingAnimation m = active();
		if (m == null) {
			return false;
		}
		m.transform(pose, attack, invert);
		return true;
	}

	private void transform(PoseStack pose, float t, int inv) {
		float str = strength.floatValue();
		float ang = angle.floatValue();
		float sq = Mth.sin(Mth.sqrt(t) * Mth.PI);   // quick rise, slow fall
		float sn = Mth.sin(t * Mth.PI);              // symmetric arc
		float sm = Mth.sin(t * t * Mth.PI);          // slow start
		pose.translate(offsetX.floatValue() * inv, offsetY.floatValue(), offsetZ.floatValue());
		switch (mode.get()) {
			case "chop" -> {
				// Raise, then cut straight down, pivoting at the handle.
				pose.translate(0, 0.12F * sq * str, -0.05F * sq);
				pose.mulPose(Axis.YP.rotationDegrees(inv * 45));
				pose.mulPose(Axis.XP.rotationDegrees(-ang * sq * str));
				pose.mulPose(Axis.YP.rotationDegrees(inv * -45));
			}
			case "spin" -> {
				pose.translate(inv * -0.1F * sn * str, 0.05F * sn, 0);
				pose.mulPose(Axis.YP.rotationDegrees(inv * 45));
				pose.mulPose(Axis.ZP.rotationDegrees(inv * -360 * t * Math.min(1, str)));
				pose.mulPose(Axis.YP.rotationDegrees(inv * -45));
			}
			case "fade" -> {
				// Smooth dip down and back with a gentle tilt, no sharp edges.
				float e = sn * sn * (3 - 2 * sn);
				pose.translate(inv * -0.08F * e * str, -0.18F * e * str, 0.12F * e * str);
				pose.mulPose(Axis.XP.rotationDegrees(-25 * e * str));
				pose.mulPose(Axis.ZP.rotationDegrees(inv * -10 * e));
			}
			case "stab" -> {
				pose.translate(inv * -0.05F * sn, 0.05F * sn, -0.35F * sq * str);
				pose.mulPose(Axis.XP.rotationDegrees(-12 * sn * str));
			}
			case "overhead" -> {
				// Lift over the head, then slam down.
				float lift = Mth.sin(Math.min(1, t * 2) * Mth.HALF_PI);
				float slam = t > 0.5F ? Mth.sin((t - 0.5F) * 2 * Mth.HALF_PI) : 0;
				pose.translate(0, 0.25F * (lift - slam) * str, 0);
				pose.mulPose(Axis.XP.rotationDegrees((ang * 0.6F * lift - ang * 1.4F * slam) * str));
				pose.mulPose(Axis.ZP.rotationDegrees(inv * -15 * sn));
			}
			case "block" -> {
				// Old-style block-hit: sword held across the screen, tapping forward.
				pose.translate(inv * -0.14F, 0.08F, 0.14F);
				pose.mulPose(Axis.XP.rotationDegrees(-102.25F + -40 * sq * str));
				pose.mulPose(Axis.YP.rotationDegrees(inv * 13.365F));
				pose.mulPose(Axis.ZP.rotationDegrees(inv * 78.05F));
			}
			case "wave" -> {
				float w = Mth.sin(t * Mth.TWO_PI);
				pose.translate(inv * -0.1F * w * str, 0.04F * sn, -0.08F * sn);
				pose.mulPose(Axis.ZP.rotationDegrees(inv * w * ang * 0.5F * str));
				pose.mulPose(Axis.XP.rotationDegrees(-20 * sn * str));
			}
			default -> {
				// "slant": a diagonal swipe.
				pose.translate(inv * -0.3F * sq * str, 0.12F * Mth.sin(Mth.sqrt(t) * Mth.TWO_PI), -0.15F * sn);
				pose.mulPose(Axis.YP.rotationDegrees(inv * (45 - 20 * sm)));
				pose.mulPose(Axis.ZP.rotationDegrees(inv * -ang * 0.5F * sq * str));
				pose.mulPose(Axis.XP.rotationDegrees(-ang * sq * str));
				pose.mulPose(Axis.YP.rotationDegrees(inv * -45));
			}
		}
	}
}
