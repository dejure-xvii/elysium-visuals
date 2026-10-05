package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.KeySetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Spyglass-like zoom while a key is held. The mouse wheel changes the zoom
 * while zooming (instead of the hotbar slot), and the mouse turns slower in
 * proportion so aiming stays as precise as without zoom.
 */
public class Zoom extends Module {
	private static final float MIN_ZOOM = 1.5f, MAX_ZOOM = 50f;

	private final KeySetting key = add(new KeySetting("key", "Бинд зума", GLFW.GLFW_KEY_C));
	private final NumberSetting amount = add(new NumberSetting("amount", "Дальность приближения", 4, MIN_ZOOM, MAX_ZOOM, 0.5, "x"));
	private final BooleanSetting wheel = add(new BooleanSetting("wheel", "Колёсико мыши", true));
	private final BooleanSetting smooth = add(new BooleanSetting("smooth", "Плавный зум", true));
	private final NumberSetting smoothSpeed = add(new NumberSetting("smooth_speed", "Скорость плавности", 12, 2, 30, 1))
			.visibleWhen(smooth::isOn);

	/** Zoom set with the wheel during this hold; reset to the slider value on release. */
	private float wheelZoom = -1;
	private float current = 1;
	private long lastMs;

	public Zoom() {
		super("zoom", "Zoom", "Приближение по удержанию клавиши, как в подзорную трубу", Category.UTILS);
	}

	private static Zoom active() {
		Zoom m = ModuleManager.get().find(Zoom.class);
		return m != null && m.isEnabled() ? m : null;
	}

	private boolean held() {
		return key.isDown();
	}

	private float target() {
		if (!held()) {
			wheelZoom = -1;
			return 1;
		}
		return wheelZoom > 0 ? wheelZoom : amount.floatValue();
	}

	/** Current magnification, updated once per frame (1 = no zoom). */
	private float update() {
		float target = target();
		long now = Util.getMillis();
		float dt = lastMs == 0 ? 0 : Math.min(0.1f, (now - lastMs) / 1000f);
		lastMs = now;
		if (!smooth.isOn()) {
			current = target;
		} else {
			current += (target - current) * (1 - (float) Math.exp(-smoothSpeed.floatValue() * dt));
			if (Math.abs(target - current) < 0.002f) {
				current = target;
			}
		}
		return current;
	}

	/** The world FOV with the zoom applied (called once per frame). */
	public static float applyFov(float fov) {
		Zoom m = active();
		if (m == null) {
			return fov;
		}
		return fov / m.update();
	}

	/** Mouse look multiplier: slower while zoomed in. */
	public static double sensitivity() {
		Zoom m = active();
		return m == null ? 1 : 1 / Math.max(1, m.current);
	}

	/** Called on a mouse wheel step outside screens; true if the zoom took it. */
	public static boolean onScroll(double delta) {
		Zoom m = active();
		if (m == null || !m.wheel.isOn() || !m.held() || delta == 0) {
			return false;
		}
		float base = m.wheelZoom > 0 ? m.wheelZoom : m.amount.floatValue();
		float next = base * (float) Math.pow(1.15, Math.signum(delta));
		m.wheelZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, next));
		return true;
	}

	@Override
	protected void onDisable() {
		current = 1;
		wheelZoom = -1;
	}
}
