package dev.elysium.visuals.client.gui.anim;

import net.minecraft.util.Util;

/**
 * A value that exponentially approaches its target — good for hover effects
 * where the target can change every frame.
 */
public final class SmoothValue {
	private final float speed;
	private float value;
	private long lastMs;

	/** @param speed how fast to approach the target; ~12 reaches it in ≈ 0.25 s */
	public SmoothValue(float initial, float speed) {
		this.value = initial;
		this.speed = speed;
	}

	public float update(float target) {
		long now = Util.getMillis();
		float dt = lastMs == 0 ? 0 : Math.min(0.1f, (now - lastMs) / 1000f);
		lastMs = now;
		value += (target - value) * (1 - (float) Math.exp(-speed * dt));
		if (Math.abs(target - value) < 0.001f) {
			value = target;
		}
		return value;
	}

	public float get() {
		return value;
	}

	public void set(float v) {
		value = v;
	}
}
