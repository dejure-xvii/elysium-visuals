package dev.elysium.visuals.client.gui.anim;

/** Easing curves mapping linear progress 0..1 to eased progress. */
@FunctionalInterface
public interface Easing {
	Easing LINEAR = t -> t;
	Easing OUT_CUBIC = t -> 1 - (float) Math.pow(1 - t, 3);
	/** Close to the launcher's cubic-bezier(0.22, 1, 0.36, 1): fast start, long soft landing. */
	Easing OUT_QUINT = t -> 1 - (float) Math.pow(1 - t, 5);
	Easing IN_CUBIC = t -> t * t * t;
	Easing IN_OUT_CUBIC = t -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	/** Slight overshoot at the end — nice for "pop-in" effects. */
	Easing OUT_BACK = t -> {
		float c1 = 1.20158f;
		float c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	};

	float apply(float t);
}
