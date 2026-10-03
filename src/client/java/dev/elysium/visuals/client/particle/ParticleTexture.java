package dev.elysium.visuals.client.particle;

import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Glow sprites (white with alpha, tinted per vertex) in
 * {@code assets/elysium-visuals/textures/particle/} and {@code .../effect/}.
 * Each gets additive render types in {@link ParticleRenderTypes}.
 */
public enum ParticleTexture {
	BLOOM("particle", "bloom"),
	STAR("particle", "star"),
	HEART("particle", "heart"),
	DOLLAR("particle", "dollar"),
	SNOWFLAKE("particle", "snowflake"),
	FIREFLY("particle", "firefly"),
	RING("effect", "ring"),
	STREAK("effect", "streak"),
	MARKER("effect", "marker"),
	RUNES("effect", "runes"),
	CHAIN("effect", "chain");

	/** The shapes offered by the Particles module (the effect sprites are for other modules). */
	public static final List<ParticleTexture> PARTICLE_SHAPES = List.of(BLOOM, STAR, HEART, DOLLAR, SNOWFLAKE, FIREFLY);

	private final String id;
	private final Identifier location;

	ParticleTexture(String folder, String id) {
		this.id = id;
		this.location = ElysiumVisuals.id("textures/" + folder + "/" + id + ".png");
	}

	public String id() {
		return id;
	}

	public Identifier location() {
		return location;
	}
}
