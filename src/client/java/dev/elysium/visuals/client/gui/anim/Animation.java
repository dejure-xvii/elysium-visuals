package dev.elysium.visuals.client.gui.anim;

import net.minecraft.util.Util;

/**
 * Time-based animation between two values with an easing curve.
 * Independent of FPS: progress is computed from wall-clock time.
 */
public final class Animation {
	private final long durationMs;
	private final Easing easing;
	private float from;
	private float to;
	private long startMs;

	public Animation(float initial, long durationMs, Easing easing) {
		this.from = initial;
		this.to = initial;
		this.durationMs = Math.max(1, durationMs);
		this.easing = easing;
		this.startMs = 0;
	}

	/** Starts animating from the current value towards {@code target}. */
	public void animateTo(float target) {
		if (target == to) {
			return;
		}
		from = get();
		to = target;
		startMs = Util.getMillis();
	}

	/** Jumps to {@code value} immediately. */
	public void set(float value) {
		from = value;
		to = value;
		startMs = 0;
	}

	public float get() {
		float p = progress();
		return from + (to - from) * easing.apply(p);
	}

	public float target() {
		return to;
	}

	public boolean isDone() {
		return progress() >= 1f;
	}

	private float progress() {
		if (startMs == 0) {
			return 1f;
		}
		return Math.min(1f, (Util.getMillis() - startMs) / (float) durationMs);
	}
}
