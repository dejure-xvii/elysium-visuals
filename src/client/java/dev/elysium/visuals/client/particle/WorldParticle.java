package dev.elysium.visuals.client.particle;

/**
 * One sprite in the world. Plain mutable fields: the engine updates thousands
 * of these per second and keeps allocation to the spawn itself.
 */
public final class WorldParticle {
	public double x, y, z;
	/** Velocity in blocks per second. */
	public double vx, vy, vz;
	/** Downwards acceleration in blocks/s² (0 = floats). */
	public float gravity;
	/** Fraction of velocity lost per second (exponential), so particles slow down smoothly. */
	public float drag = 1.6f;
	public float age;
	public float life;
	/** Half the quad size in blocks. */
	public float size;
	public float angle;
	/** Rotation speed in radians per second. */
	public float spin;
	public ParticleTexture texture;
	/** Fixed ARGB color, or 0 to follow the module's color setting. */
	public int color;
	/** Random 0..1 offset into the theme gradient so neighbours differ. */
	public float phase;
	/** Idle (firefly) particles wander instead of slowing to a stop. */
	public boolean idle;

	public boolean isDead() {
		return age >= life;
	}

	/** 0 → 1 over the first 15% of life, 1 → 0 over the last 40%, eased (0 before a delayed start). */
	public float fade() {
		if (age < 0) {
			return 0;
		}
		float t = age / life;
		float in = Math.min(1f, t / 0.15f);
		float out = Math.min(1f, (1f - t) / 0.4f);
		float f = Math.max(0f, Math.min(in, out));
		return f * f * (3 - 2 * f);
	}
}
