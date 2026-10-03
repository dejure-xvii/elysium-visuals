package dev.elysium.visuals.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Simulates and draws {@link WorldParticle}s. Motion is integrated with the
 * real frame time (not ticks), so it is smooth at any FPS.
 */
public final class ParticleEngine {
	/** Hard cap on live particles; new spawns are dropped beyond it. */
	public static final int MAX_PARTICLES = 1000;
	/** Velocity kept after hitting a block (the rest is lost in the bounce). */
	private static final double BOUNCE = 0.45;
	/** Glow halo: this much larger than the sprite and this transparent. */
	private static final float GLOW_SCALE = 3.2f;
	private static final float GLOW_ALPHA = 0.22f;

	private final List<WorldParticle> particles = new ArrayList<>();
	private final RandomSource random = RandomSource.create();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	public RandomSource random() {
		return random;
	}

	public int size() {
		return particles.size();
	}

	public int count(boolean idle) {
		int n = 0;
		for (WorldParticle p : particles) {
			if (p.idle == idle) {
				n++;
			}
		}
		return n;
	}

	/** Adds a particle unless the cap is reached; returns it (or null) for further tweaks. */
	public WorldParticle spawn(double x, double y, double z, double vx, double vy, double vz,
							   float life, float size, ParticleTexture texture) {
		if (particles.size() >= MAX_PARTICLES) {
			return null;
		}
		WorldParticle p = new WorldParticle();
		p.x = x;
		p.y = y;
		p.z = z;
		p.vx = vx;
		p.vy = vy;
		p.vz = vz;
		p.life = life;
		p.size = size;
		p.texture = texture;
		p.phase = random.nextFloat();
		p.angle = random.nextFloat() * (float) (Math.PI * 2);
		p.spin = (random.nextFloat() - 0.5f) * 3f;
		particles.add(p);
		return p;
	}

	public void clear() {
		particles.clear();
	}

	// --- Simulation ---------------------------------------------------------

	/** Advances every particle by {@code dt} seconds and removes the finished ones. */
	public void update(ClientLevel level, float dt, boolean rotate) {
		if (dt <= 0) {
			return;
		}
		double damping;
		for (int i = particles.size() - 1; i >= 0; i--) {
			WorldParticle p = particles.get(i);
			p.age += dt;
			if (p.isDead()) {
				// Swap-remove: order doesn't matter for additive sprites.
				int last = particles.size() - 1;
				particles.set(i, particles.get(last));
				particles.remove(last);
				continue;
			}
			if (p.age < 0) {
				continue; // scheduled for later: invisible and still until its age reaches 0
			}
			if (p.idle) {
				// Fireflies drift: a gentle random push keeps them wandering.
				p.vx += (random.nextFloat() - 0.5f) * 0.8f * dt;
				p.vy += (random.nextFloat() - 0.5f) * 0.5f * dt;
				p.vz += (random.nextFloat() - 0.5f) * 0.8f * dt;
			}
			p.vy -= p.gravity * dt;
			damping = Math.exp(-p.drag * dt);
			p.vx *= damping;
			p.vy *= damping;
			p.vz *= damping;
			move(level, p, dt);
			if (rotate) {
				p.angle += p.spin * dt;
			}
		}
	}

	/** Moves one axis at a time so a particle sliding along a wall keeps its other motion. */
	private void move(ClientLevel level, WorldParticle p, float dt) {
		double nx = p.x + p.vx * dt;
		if (solid(level, nx, p.y, p.z)) {
			p.vx = -p.vx * BOUNCE;
		} else {
			p.x = nx;
		}
		double ny = p.y + p.vy * dt;
		if (solid(level, p.x, ny, p.z)) {
			p.vy = -p.vy * BOUNCE;
			// Resting on the floor: also lose sideways speed (friction).
			p.vx *= 0.8;
			p.vz *= 0.8;
		} else {
			p.y = ny;
		}
		double nz = p.z + p.vz * dt;
		if (solid(level, p.x, p.y, nz)) {
			p.vz = -p.vz * BOUNCE;
		} else {
			p.z = nz;
		}
	}

	private boolean solid(ClientLevel level, double x, double y, double z) {
		probe.set(Math.floor(x), Math.floor(y), Math.floor(z));
		BlockState state = level.getBlockState(probe);
		if (state.isAir()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, probe);
		if (shape.isEmpty()) {
			return false;
		}
		double lx = x - probe.getX(), ly = y - probe.getY(), lz = z - probe.getZ();
		for (AABB box : shape.toAabbs()) {
			if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) {
				return true;
			}
		}
		return false;
	}

	// --- Rendering ----------------------------------------------------------

	/**
	 * Submits camera-facing quads, one batch per texture.
	 *
	 * @param colors final ARGB color (before fading) for a particle
	 */
	public void render(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, Quaternionf cameraRotation,
					   boolean throughWalls, boolean glow, ToIntFunction<WorldParticle> colors) {
		if (particles.isEmpty()) {
			return;
		}
		// Camera right/up axes: quads built from them always face the viewer.
		Vector3f right = cameraRotation.transform(new Vector3f(1, 0, 0));
		Vector3f up = cameraRotation.transform(new Vector3f(0, 1, 0));

		Map<ParticleTexture, List<WorldParticle>> byTexture = new EnumMap<>(ParticleTexture.class);
		for (WorldParticle p : particles) {
			byTexture.computeIfAbsent(p.texture, t -> new ArrayList<>()).add(p);
		}
		if (glow) {
			// The geometry callback runs later in the frame: hand it a snapshot, not the live list.
			List<WorldParticle> all = List.copyOf(particles);
			collector.submitCustomGeometry(poseStack, ParticleRenderTypes.get(ParticleTexture.BLOOM, throughWalls),
					(pose, vc) -> {
						for (WorldParticle p : all) {
							quad(pose, vc, p, camera, right, up, p.size * GLOW_SCALE, 0f,
									scaleAlpha(colors.applyAsInt(p), p.fade() * GLOW_ALPHA));
						}
					});
		}
		for (Map.Entry<ParticleTexture, List<WorldParticle>> e : byTexture.entrySet()) {
			List<WorldParticle> list = e.getValue();
			collector.submitCustomGeometry(poseStack, ParticleRenderTypes.get(e.getKey(), throughWalls), (pose, vc) -> {
				for (WorldParticle p : list) {
					quad(pose, vc, p, camera, right, up, p.size, p.angle, scaleAlpha(colors.applyAsInt(p), p.fade()));
				}
			});
		}
	}

	private static int scaleAlpha(int argb, float f) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, f)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, WorldParticle p, Vec3 camera,
							 Vector3f right, Vector3f up, float half, float angle, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		float cx = (float) (p.x - camera.x), cy = (float) (p.y - camera.y), cz = (float) (p.z - camera.z);
		// Rotate the in-plane axes by the particle's angle.
		float cos = (float) Math.cos(angle) * half, sin = (float) Math.sin(angle) * half;
		float rx = right.x * cos + up.x * sin, ry = right.y * cos + up.y * sin, rz = right.z * cos + up.z * sin;
		float ux = up.x * cos - right.x * sin, uy = up.y * cos - right.y * sin, uz = up.z * cos - right.z * sin;
		vc.addVertex(pose, cx - rx - ux, cy - ry - uy, cz - rz - uz).setUv(0, 1).setColor(color);
		vc.addVertex(pose, cx + rx - ux, cy + ry - uy, cz + rz - uz).setUv(1, 1).setColor(color);
		vc.addVertex(pose, cx + rx + ux, cy + ry + uy, cz + rz + uz).setUv(1, 0).setColor(color);
		vc.addVertex(pose, cx - rx + ux, cy - ry + uy, cz - rz + uz).setUv(0, 0).setColor(color);
	}
}
