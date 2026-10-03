package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * A glowing effect around the entity you hit last. It fades out a couple of
 * seconds after the last hit, or when the target dies or gets far away. All
 * motion is computed from time (tails are evaluated from the orbit formulas),
 * so a frame allocates almost nothing; the cubes use a fixed pool.
 */
public class TargetESP extends Module {
	private static final double MAX_DISTANCE = 48;
	private static final int CUBES = 48;

	private final ModeSetting mode = add(new ModeSetting("mode", "Режим",
			List.of(
					option("marker", "Маркер"),
					option("ghosts", "Призраки"),
					option("orbits", "Призрачные орбиты"),
					option("spirals", "Спирали"),
					option("crystals", "Кристаллы"),
					option("cubes", "Кубики"),
					option("ring", "Кольцо"),
					option("chain", "Цепь"),
					option("magic", "Магический круг")),
			"marker"));
	private final NumberSetting size = add(new NumberSetting("size", "Размер", 1, 0.4, 2.5, 0.05, "x"));
	private final NumberSetting speed = add(new NumberSetting("speed", "Скорость вращения", 1, 0.1, 4, 0.05, "x"));
	private final BooleanSetting throughWalls = add(new BooleanSetting("through_walls", "Сквозь стены", true));

	private LivingEntity target;
	private float visibility;
	private float time;
	private long lastFrameNs;
	private final Vector3f right = new Vector3f(), up = new Vector3f();

	// Cube pool: position (relative to the target), velocity, spin axis angle, age.
	private final float[] cx = new float[CUBES], cy = new float[CUBES], cz = new float[CUBES];
	private final float[] vx = new float[CUBES], vy = new float[CUBES], vz = new float[CUBES];
	private final float[] spin = new float[CUBES], cubeAge = new float[CUBES];
	private final boolean[] cubeAlive = new boolean[CUBES];
	private float cubeTimer;
	private final RandomSource random = RandomSource.create();

	public TargetESP() {
		super("target_esp", "TargetESP", "Эффект вокруг последней ударенной цели: 9 режимов", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	protected void onDisable() {
		target = null;
		visibility = 0;
		java.util.Arrays.fill(cubeAlive, false);
	}

	private boolean valid(LivingEntity e, Minecraft mc) {
		return e != null && e.isAlive() && !e.isRemoved() && mc.player != null && e.level() == mc.player.level()
				&& e.distanceToSqr(mc.player) < MAX_DISTANCE * MAX_DISTANCE;
	}

	private void render(LevelRenderContext ctx) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return;
		}
		LivingEntity current = TargetTracker.current();
		boolean show = valid(current, mc);
		if (show) {
			target = current;
		}
		// Fade in quickly, fade out a bit slower; keep the last target while fading.
		float goal = show ? 1f : 0f;
		visibility += (goal - visibility) * (1f - (float) Math.exp(-dt * (show ? 10f : 4f)));
		if (target == null || visibility < 0.01f || target.isRemoved()) {
			if (visibility < 0.01f) {
				target = null;
			}
			return;
		}
		if (!mc.isPaused()) {
			time += dt * speed.floatValue();
		}

		CameraRenderState camera = ctx.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float bx = (float) (target.xo + (target.getX() - target.xo) * partial - cam.x);
		float by = (float) (target.yo + (target.getY() - target.yo) * partial - cam.y);
		float bz = (float) (target.zo + (target.getZ() - target.zo) * partial - cam.z);
		camera.orientation.transform(right.set(1, 0, 0));
		camera.orientation.transform(up.set(0, 1, 0));
		float w = target.getBbWidth() * size.floatValue(), h = target.getBbHeight();
		float vis = visibility * visibility * (3 - 2 * visibility);
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		boolean xray = throughWalls.isOn();
		SubmitNodeCollector out = ctx.submitNodeCollector();
		PoseStack poseStack = ctx.poseStack();

		switch (mode.get()) {
			case "ghosts" -> ghosts(out, poseStack, bx, by, bz, w, h, vis, a, b, xray);
			case "orbits" -> orbits(out, poseStack, bx, by, bz, w, h, vis, a, b, xray);
			case "spirals" -> spirals(out, poseStack, bx, by, bz, w, h, vis, a, b, xray);
			case "crystals" -> crystals(out, poseStack, bx, by, bz, w, h, vis, a, b, xray);
			case "cubes" -> cubes(out, poseStack, bx, by, bz, w, h, vis, a, b, xray, mc.isPaused() ? 0 : dt);
			case "ring" -> ring(out, poseStack, bx, by, bz, w, h, vis, a, b, xray);
			case "chain" -> chain(out, poseStack, bx, by, bz, w, h, vis, a, b, xray);
			case "magic" -> magic(out, poseStack, bx, by, bz, w, vis, a, b, xray);
			default -> marker(out, poseStack, bx, by, bz, w, h, vis, a, b);
		}
	}

	/** Camera-facing sprite rotated by {@code angle} in the screen plane. */
	private void billboard(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float half, float angle, int color) {
		float c = (float) Math.cos(angle) * half, s = (float) Math.sin(angle) * half;
		GlowGeometry.sprite(pose, vc, x, y, z,
				right.x * c + up.x * s, right.y * c + up.y * s, right.z * c + up.z * s,
				up.x * c - right.x * s, up.y * c - right.y * s, up.z * c - right.z * s, color);
	}

	// --- Modes ----------------------------------------------------------------

	/** Rotating reticle facing the camera; always visible through walls. */
	private void marker(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b) {
		float half = (w + h) * 0.42f * (0.8f + 0.2f * vis);
		float cy = y + h * 0.5f;
		float pulse = 1f + 0.06f * (float) Math.sin(time * 5);
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, true), (pose, vc) ->
				billboard(pose, vc, x, cy, z, half * 1.3f, 0, GlowGeometry.scaleAlpha(b, 0.35f * vis)));
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.MARKER, true), (pose, vc) -> {
			billboard(pose, vc, x, cy, z, half * pulse, time * 1.5f, GlowGeometry.scaleAlpha(a, vis));
			billboard(pose, vc, x, cy, z, half * 0.62f * pulse, -time * 2.2f, GlowGeometry.scaleAlpha(b, 0.8f * vis));
		});
	}

	/** Glowing spirits on an orbit, each followed by a fading tail. */
	private void ghosts(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b, boolean xray) {
		int ghosts = 3, tail = 18;
		float r = w * 0.85f + 0.25f;
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, xray), (pose, vc) -> {
			for (int g = 0; g < ghosts; g++) {
				float phase = g * (float) (Math.PI * 2 / ghosts);
				for (int k = tail; k >= 0; k--) {
					float t = time * 2.2f - k * 0.045f;
					float ang = t + phase;
					float px = x + (float) Math.cos(ang) * r, pz = z + (float) Math.sin(ang) * r;
					float py = y + h * (0.55f + 0.3f * (float) Math.sin(t * 1.3f + phase));
					float f = 1f - k / (float) (tail + 1);
					int color = ThemeColors.gradient(a, b, g / (float) ghosts + k * 0.02f) | 0xFF000000;
					billboard(pose, vc, px, py, pz, (k == 0 ? 0.32f : 0.2f) * f * size.floatValue(), 0,
							GlowGeometry.scaleAlpha(color, (k == 0 ? 1f : 0.55f) * f * vis));
				}
			}
		});
	}

	/** Small particles on tilted orbits at different heights, with long tails. */
	private void orbits(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b, boolean xray) {
		int count = 6, tail = 30;
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.FIREFLY, xray), (pose, vc) -> {
			for (int i = 0; i < count; i++) {
				float r = w * (0.7f + 0.12f * (i % 3)) + 0.2f;
				float height = h * (0.15f + 0.7f * i / (count - 1f));
				float tilt = 0.25f * (float) Math.sin(i * 1.7f);
				float dir = i % 2 == 0 ? 1 : -1;
				int color = i % 2 == 0 ? a : b;
				for (int k = tail; k >= 0; k--) {
					float ang = dir * (time * (1.6f + i * 0.15f) - k * 0.035f) + i;
					float px = x + (float) Math.cos(ang) * r, pz = z + (float) Math.sin(ang) * r;
					float py = y + height + (float) Math.sin(ang) * tilt * r;
					float f = 1f - k / (float) (tail + 1);
					billboard(pose, vc, px, py, pz, (k == 0 ? 0.13f : 0.09f) * f * size.floatValue(), 0,
							GlowGeometry.scaleAlpha(color, f * f * vis));
				}
			}
		});
	}

	/** Glowing helix arcs winding around the target. */
	private void spirals(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b, boolean xray) {
		int arcs = 3, segments = 40;
		float r = w * 0.75f + 0.15f;
		float width = 0.045f * size.floatValue();
		out.submitCustomGeometry(ps, WorldPipelines.solid(xray), (pose, vc) -> {
			for (int s = 0; s < arcs; s++) {
				float phase = s * (float) (Math.PI * 2 / arcs) + time * 1.8f;
				float px = 0, py = 0, pz = 0;
				for (int i = 0; i <= segments; i++) {
					float t = i / (float) segments;
					float ang = phase + t * (float) Math.PI * 2.5f;
					float rr = r * (0.85f + 0.15f * (float) Math.sin(t * Math.PI));
					float nx = x + (float) Math.cos(ang) * rr, ny = y + t * h * 1.05f, nz = z + (float) Math.sin(ang) * rr;
					if (i > 0) {
						// Arcs fade in at the bottom and out at the top.
						float f = (float) Math.sin(t * Math.PI) * vis;
						int c0 = GlowGeometry.scaleAlpha(ThemeColors.gradient(a, b, t) | 0xFF000000, f);
						GlowGeometry.glowLine(pose, vc, px, py, pz, nx, ny, nz, width, 1, c0, c0);
					}
					px = nx; py = ny; pz = nz;
				}
			}
		});
	}

	/** Floating, spinning octahedron crystals around the target. */
	private void crystals(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b, boolean xray) {
		int count = 4;
		float r = w * 0.9f + 0.3f;
		float cs = 0.16f * size.floatValue();
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, xray), (pose, vc) -> {
			for (int i = 0; i < count; i++) {
				float ang = time * 0.9f + i * (float) (Math.PI * 2 / count);
				float py = y + h * 0.55f + 0.15f * (float) Math.sin(time * 2 + i * 1.3f);
				billboard(pose, vc, x + (float) Math.cos(ang) * r, py, z + (float) Math.sin(ang) * r, cs * 3.2f, 0,
						GlowGeometry.scaleAlpha(i % 2 == 0 ? a : b, 0.4f * vis));
			}
		});
		out.submitCustomGeometry(ps, WorldPipelines.solid(xray), (pose, vc) -> {
			for (int i = 0; i < count; i++) {
				float ang = time * 0.9f + i * (float) (Math.PI * 2 / count);
				float px = x + (float) Math.cos(ang) * r, pz = z + (float) Math.sin(ang) * r;
				float py = y + h * 0.55f + 0.15f * (float) Math.sin(time * 2 + i * 1.3f);
				octahedron(pose, vc, px, py, pz, cs, cs * 1.8f, time * 2.5f + i,
						GlowGeometry.scaleAlpha(i % 2 == 0 ? a : b, 0.75f * vis), GlowGeometry.scaleAlpha(0xFFFFFFFF, 0.55f * vis));
			}
		});
	}

	/** An octahedron (8 triangles drawn as degenerate quads), spinning around Y. */
	private static void octahedron(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float r, float tall, float spin,
								   int side, int tip) {
		float topY = y + tall, botY = y - tall;
		for (int i = 0; i < 4; i++) {
			float a0 = spin + i * (float) Math.PI / 2, a1 = a0 + (float) Math.PI / 2;
			float x0 = x + (float) Math.cos(a0) * r, z0 = z + (float) Math.sin(a0) * r;
			float x1 = x + (float) Math.cos(a1) * r, z1 = z + (float) Math.sin(a1) * r;
			// Alternate face brightness for a faceted look.
			int c = i % 2 == 0 ? side : GlowGeometry.scaleAlpha(side, 0.65f);
			vc.addVertex(pose, x0, y, z0).setColor(c);
			vc.addVertex(pose, x1, y, z1).setColor(c);
			vc.addVertex(pose, x, topY, z).setColor(tip);
			vc.addVertex(pose, x, topY, z).setColor(tip);
			vc.addVertex(pose, x0, y, z0).setColor(c);
			vc.addVertex(pose, x1, y, z1).setColor(c);
			vc.addVertex(pose, x, botY, z).setColor(side);
			vc.addVertex(pose, x, botY, z).setColor(side);
		}
	}

	/** Small glowing cubes thrown out of the target, slowly fading. */
	private void cubes(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b,
					   boolean xray, float dt) {
		cubeTimer += dt;
		// Emit while the target is shown: about 14 per second.
		while (cubeTimer > 0.07f && visibility > 0.5f) {
			cubeTimer -= 0.07f;
			emitCube(w, h);
		}
		cubeTimer = Math.min(cubeTimer, 0.07f);
		float life = 1.6f;
		for (int i = 0; i < CUBES; i++) {
			if (cubeAlive[i]) {
				cubeAge[i] += dt;
				if (cubeAge[i] > life) {
					cubeAlive[i] = false;
					continue;
				}
				float damp = (float) Math.exp(-dt * 1.5f);
				vx[i] *= damp;
				vy[i] *= damp;
				vz[i] *= damp;
				cx[i] += vx[i] * dt;
				cy[i] += vy[i] * dt;
				cz[i] += vz[i] * dt;
				spin[i] += dt * 3;
			}
		}
		float s = 0.07f * size.floatValue();
		out.submitCustomGeometry(ps, WorldPipelines.glow(xray), (pose, vc) -> {
			for (int i = 0; i < CUBES; i++) {
				if (cubeAlive[i]) {
					float f = 1f - cubeAge[i] / life;
					int c = GlowGeometry.scaleAlpha(i % 2 == 0 ? a : b, f * f * vis);
					cube(pose, vc, x + cx[i], y + cy[i], z + cz[i], s * (0.6f + 0.4f * f), spin[i], c);
				}
			}
		});
	}

	private void emitCube(float w, float h) {
		for (int i = 0; i < CUBES; i++) {
			if (!cubeAlive[i]) {
				double ang = random.nextDouble() * Math.PI * 2;
				float sp = 0.6f + random.nextFloat() * 0.9f;
				cx[i] = 0;
				cy[i] = h * (0.3f + random.nextFloat() * 0.5f);
				cz[i] = 0;
				vx[i] = (float) Math.cos(ang) * sp;
				vz[i] = (float) Math.sin(ang) * sp;
				vy[i] = 0.3f + random.nextFloat() * 0.6f;
				spin[i] = random.nextFloat() * 6;
				cubeAge[i] = 0;
				cubeAlive[i] = true;
				return;
			}
		}
	}

	/** A cube spinning around Y (6 faces). */
	private static void cube(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float s, float spin, int color) {
		float c = (float) Math.cos(spin) * s, n = (float) Math.sin(spin) * s;
		// Corners of the bottom square, rotated.
		float ax = x + c - n, az = z + n + c, bx = x - c - n, bz = z - n + c;
		float dx = x + c + n, dz = z + n - c, ex = x - c + n, ez = z - n - c;
		float y0 = y - s, y1 = y + s;
		quad(pose, vc, ax, y0, az, bx, y0, bz, ex, y0, ez, dx, y0, dz, color);
		quad(pose, vc, ax, y1, az, bx, y1, bz, ex, y1, ez, dx, y1, dz, color);
		quad(pose, vc, ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, color);
		quad(pose, vc, bx, y0, bz, ex, y0, ez, ex, y1, ez, bx, y1, bz, color);
		quad(pose, vc, ex, y0, ez, dx, y0, dz, dx, y1, dz, ex, y1, ez, color);
		quad(pose, vc, dx, y0, dz, ax, y0, az, ax, y1, az, dx, y1, dz, color);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3, int color) {
		vc.addVertex(pose, x0, y0, z0).setColor(color);
		vc.addVertex(pose, x1, y1, z1).setColor(color);
		vc.addVertex(pose, x2, y2, z2).setColor(color);
		vc.addVertex(pose, x3, y3, z3).setColor(color);
	}

	/** A glowing ring sliding smoothly up and down the target. */
	private void ring(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b, boolean xray) {
		int segments = 48;
		float r = w * 0.7f + 0.2f;
		float width = 0.05f * size.floatValue();
		out.submitCustomGeometry(ps, WorldPipelines.solid(xray), (pose, vc) -> {
			// The ring and two fainter echoes trailing behind its motion.
			for (int echo = 2; echo >= 0; echo--) {
				float t = time * 1.4f - echo * 0.12f;
				float ry = y + h * (0.5f + 0.48f * (float) Math.sin(t));
				float f = (echo == 0 ? 1f : 0.35f / echo) * vis;
				float px = x + r, pz = z;
				for (int i = 1; i <= segments; i++) {
					float ang = i / (float) segments * (float) Math.PI * 2;
					float nx = x + (float) Math.cos(ang) * r, nz = z + (float) Math.sin(ang) * r;
					int c = GlowGeometry.scaleAlpha(ThemeColors.gradient(a, b, i / (float) segments + time * 0.2f) | 0xFF000000, f);
					GlowGeometry.glowLine(pose, vc, px, ry, pz, nx, ry, nz, width, echo == 0 ? 1 : 0, c, c);
					px = nx;
					pz = nz;
				}
			}
		});
	}

	/** Chains wound around the target: links along two helices, alternating orientation. */
	private void chain(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float h, float vis, int a, int b, boolean xray) {
		int links = 16;
		float r = w * 0.72f + 0.12f;
		float linkHalf = 0.12f * size.floatValue();
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.CHAIN, xray), (pose, vc) -> {
			for (int helix = 0; helix < 2; helix++) {
				float dir = helix == 0 ? 1 : -1;
				for (int i = 0; i < links; i++) {
					// Links scroll along the helix.
					float t = ((i + time * 1.2f) % links) / links;
					float ang = dir * t * (float) Math.PI * 3 + helix * (float) Math.PI;
					float px = x + (float) Math.cos(ang) * r, py = y + t * h, pz = z + (float) Math.sin(ang) * r;
					// Tangent of the helix (direction of the chain).
					float tx = -(float) Math.sin(ang) * dir * r * 3 * (float) Math.PI, ty = h, tz = (float) Math.cos(ang) * dir * r * 3 * (float) Math.PI;
					float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
					tx /= tl; ty /= tl; tz /= tl;
					// Every other link is turned 90° around the chain.
					float ux, uy, uz;
					if (i % 2 == 0) {
						ux = (float) Math.cos(ang); uy = 0; uz = (float) Math.sin(ang);
					} else {
						float rx = (float) Math.cos(ang), rz = (float) Math.sin(ang);
						ux = ty * rz; uy = tz * rx - tx * rz; uz = -ty * rx;
					}
					float fade = (float) Math.sin(t * Math.PI);
					int c = GlowGeometry.scaleAlpha(i % 2 == 0 ? a : b, vis * (0.4f + 0.6f * fade));
					GlowGeometry.sprite(pose, vc, px, py, pz, ux * linkHalf * 0.6f, uy * linkHalf * 0.6f, uz * linkHalf * 0.6f,
							tx * linkHalf, ty * linkHalf, tz * linkHalf, c);
				}
			}
		});
	}

	/** Rotating rune circles under the target's feet. */
	private void magic(SubmitNodeCollector out, PoseStack ps, float x, float y, float z, float w, float vis, int a, int b, boolean xray) {
		float r = w * 1.4f + 0.5f;
		float pulse = 0.85f + 0.15f * (float) Math.sin(time * 3);
		float grow = 0.6f + 0.4f * vis;
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, xray), (pose, vc) ->
				GlowGeometry.flatSprite(pose, vc, x, y + 0.03f, z, r * 1.3f * grow, r * 1.3f * grow, 0, GlowGeometry.scaleAlpha(a, 0.3f * vis)));
		out.submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.RUNES, xray), (pose, vc) -> {
			GlowGeometry.flatSprite(pose, vc, x, y + 0.04f, z, r * grow, r * grow, time * 0.6f, GlowGeometry.scaleAlpha(a, vis * pulse));
			GlowGeometry.flatSprite(pose, vc, x, y + 0.05f, z, r * 0.55f * grow, r * 0.55f * grow, -time * 1.1f,
					GlowGeometry.scaleAlpha(b, 0.85f * vis));
		});
	}
}
