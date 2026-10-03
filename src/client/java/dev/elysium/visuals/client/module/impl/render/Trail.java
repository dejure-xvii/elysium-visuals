package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A glowing ribbon under your feet: marks are dropped at equal steps along the
 * path, turned along the direction of motion, with a soft glow, a light pulse
 * and a smooth fade. Kept in a ring buffer of {@value #MAX} marks.
 */
public class Trail extends Module {
	private static final int MAX = 220;
	private static final double TELEPORT = 8;

	private final NumberSetting density = add(new NumberSetting("density", "Плотность", 4, 1, 10, 0.5, " /бл."));
	private final NumberSetting size = add(new NumberSetting("size", "Размер", 0.35, 0.1, 1, 0.01, " бл."));
	private final NumberSetting lifetime = add(new NumberSetting("lifetime", "Время жизни", 1.5, 0.3, 5, 0.1, " с"));
	private final NumberSetting glow = add(new NumberSetting("glow", "Свечение", 1, 0, 2, 0.05));
	private final NumberSetting glowSize = add(new NumberSetting("glow_size", "Размер свечения", 2, 1, 4, 0.1, "x"));

	// Ring buffer of marks: position, heading, age.
	private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
	private final float[] yaw = new float[MAX], age = new float[MAX];
	private int head;
	private int count;
	private double lastX, lastY, lastZ;
	private boolean hasLast;
	private ClientLevel lastLevel;
	private long lastFrameNs;

	public Trail() {
		super("trail", "Trail", "Светящаяся лента под ногами при движении", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	protected void onDisable() {
		clear();
	}

	private void clear() {
		count = 0;
		hasLast = false;
	}

	/** Called each frame with the interpolated feet position: drops marks every 1/density blocks. */
	private void track(LocalPlayer player, float partial) {
		double px = player.xo + (player.getX() - player.xo) * partial;
		double py = player.yo + (player.getY() - player.yo) * partial;
		double pz = player.zo + (player.getZ() - player.zo) * partial;
		if (!hasLast || player.level() != lastLevel
				|| (px - lastX) * (px - lastX) + (pz - lastZ) * (pz - lastZ) > TELEPORT * TELEPORT) {
			// Fresh start (first frame, other world or a teleport): drop the old trail.
			count = 0;
			lastX = px;
			lastY = py;
			lastZ = pz;
			hasLast = true;
			lastLevel = (ClientLevel) player.level();
			return;
		}
		double step = 1.0 / density.get();
		double dx = px - lastX, dz = pz - lastZ;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < step) {
			return;
		}
		float heading = (float) Math.atan2(dz, dx);
		int n = (int) (dist / step);
		for (int i = 1; i <= n; i++) {
			double t = i * step / dist;
			add(lastX + dx * t, lastY + (py - lastY) * t, lastZ + dz * t, heading);
		}
		double used = n * step / dist;
		lastX += dx * used;
		lastY += (py - lastY) * used;
		lastZ += dz * used;
	}

	private void add(double px, double py, double pz, float heading) {
		x[head] = px;
		y[head] = py + 0.03;
		z[head] = pz;
		yaw[head] = heading;
		age[head] = 0;
		head = (head + 1) % MAX;
		count = Math.min(MAX, count + 1);
	}

	private void render(LevelRenderContext ctx) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null) {
			return;
		}
		if (!mc.isPaused()) {
			track(mc.player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		float life = lifetime.floatValue();
		// Age marks; the oldest sit at the tail of the ring buffer, so expired ones drop off the count.
		while (count > 0) {
			int tail = Math.floorMod(head - count, MAX);
			if (age[tail] + dt < life) {
				break;
			}
			count--;
		}
		if (count == 0) {
			return;
		}
		for (int k = 0; k < count; k++) {
			age[Math.floorMod(head - 1 - k, MAX)] += mc.isPaused() ? 0 : dt;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		float s = size.floatValue(), g = glow.floatValue(), gs = glowSize.floatValue();
		float time = now / 1e9f;
		int n = count;

		if (g > 0) {
			ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), ParticleRenderTypes.get(ParticleTexture.BLOOM, false), (pose, vc) -> {
				for (int k = 0; k < n; k++) {
					int i = Math.floorMod(head - 1 - k, MAX);
					float f = fade(age[i] / life);
					float half = s * gs * 0.5f;
					GlowGeometry.flatSprite(pose, vc, (float) (x[i] - cam.x), (float) (y[i] - cam.y), (float) (z[i] - cam.z),
							half, half, 0, GlowGeometry.scaleAlpha(color(a, b, k, time), 0.22f * g * f));
				}
			});
		}
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), ParticleRenderTypes.get(ParticleTexture.STREAK, false), (pose, vc) -> {
			for (int k = 0; k < n; k++) {
				int i = Math.floorMod(head - 1 - k, MAX);
				float f = fade(age[i] / life);
				float pulse = 1f + 0.08f * (float) Math.sin(time * 6 + i * 0.5f);
				// The streak texture points along V; turn V to the heading.
				GlowGeometry.flatSprite(pose, vc, (float) (x[i] - cam.x), (float) (y[i] - cam.y) + 0.001f, (float) (z[i] - cam.z),
						s * 0.35f * pulse, s * 0.75f * pulse, yaw[i] - (float) Math.PI / 2,
						GlowGeometry.scaleAlpha(color(a, b, k, time), f));
			}
		});
	}

	/** Shimmering gradient of the theme colors along the trail. */
	private static int color(int a, int b, int index, float time) {
		return 0xFF000000 | ThemeColors.gradient(a, b, index * 0.035f - time * 0.4f);
	}

	private static float fade(float t) {
		float u = Math.max(0, 1 - t);
		return Math.min(1f, t / 0.05f + 0.2f) * u * u;
	}
}
