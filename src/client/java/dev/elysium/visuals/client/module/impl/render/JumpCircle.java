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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A glowing theme-colored circle on the ground where you jump: it expands,
 * turns, pulses slightly and fades out. At most {@value #MAX} at once.
 */
public class JumpCircle extends Module {
	private static final int MAX = 8;

	private final NumberSetting radius = add(new NumberSetting("radius", "Радиус", 1.2, 0.4, 3, 0.05, " бл."));
	private final NumberSetting speed = add(new NumberSetting("speed", "Скорость", 1, 0.3, 3, 0.05, "x"));
	private final NumberSetting fadeSpeed = add(new NumberSetting("fade_speed", "Скорость исчезновения", 1, 0.3, 3, 0.05, "x"));
	private final NumberSetting glow = add(new NumberSetting("glow", "Сила свечения", 1, 0, 2, 0.05));

	/** Fixed pool, reused: no allocation per jump or per frame. */
	private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
	private final float[] age = new float[MAX];
	private final boolean[] alive = new boolean[MAX];
	private boolean wasOnGround = true;
	private long lastFrameNs;

	public JumpCircle() {
		super("jump_circle", "JumpCircle", "Светящийся круг на земле при прыжке", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(alive, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean onGround = player.onGround();
		// A jump: we just left the ground moving upwards.
		if (wasOnGround && !onGround && player.getDeltaMovement().y > 0.2) {
			spawn(player.xo, player.yo, player.zo);
		}
		wasOnGround = onGround;
	}

	private void spawn(double px, double py, double pz) {
		int slot = 0;
		float oldest = -1;
		for (int i = 0; i < MAX; i++) {
			if (!alive[i]) {
				slot = i;
				oldest = Float.MAX_VALUE;
				break;
			}
			if (age[i] > oldest) {
				oldest = age[i];
				slot = i;
			}
		}
		x[slot] = px;
		y[slot] = py + 0.02;
		z[slot] = pz;
		age[slot] = 0;
		alive[slot] = true;
	}

	private void render(LevelRenderContext ctx) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;
		if (!isEnabled() || Minecraft.getInstance().isPaused()) {
			return;
		}
		boolean any = false;
		float life = 1.4f / fadeSpeed.floatValue();
		for (int i = 0; i < MAX; i++) {
			if (alive[i]) {
				age[i] += dt * speed.floatValue();
				if (age[i] >= life) {
					alive[i] = false;
				} else {
					any = true;
				}
			}
		}
		if (!any) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		float r = radius.floatValue(), g = glow.floatValue();
		float time = now / 1e9f;

		// Soft glow layer first, then the rings on top.
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), ParticleRenderTypes.get(ParticleTexture.BLOOM, false), (pose, vc) -> {
			for (int i = 0; i < MAX; i++) {
				if (alive[i] && g > 0) {
					float t = age[i] / life, size = r * expand(t) * 1.25f;
					GlowGeometry.flatSprite(pose, vc, cx(i, cam), cy(i, cam), cz(i, cam), size, size, 0,
							GlowGeometry.scaleAlpha(a, 0.35f * g * fade(t)));
				}
			}
		});
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), ParticleRenderTypes.get(ParticleTexture.RING, false), (pose, vc) -> {
			for (int i = 0; i < MAX; i++) {
				if (!alive[i]) {
					continue;
				}
				float t = age[i] / life;
				float pulse = 1f + 0.05f * (float) Math.sin(time * 9 + i);
				float size = r * expand(t) * pulse, f = fade(t);
				float spin = age[i] * 1.6f;
				GlowGeometry.flatSprite(pose, vc, cx(i, cam), cy(i, cam), cz(i, cam), size, size, spin, GlowGeometry.scaleAlpha(a, f));
				// Inner ring in the second color, turning the other way, with its own glow layer.
				GlowGeometry.flatSprite(pose, vc, cx(i, cam), cy(i, cam) + 0.002f, cz(i, cam), size * 0.72f, size * 0.72f, -spin,
						GlowGeometry.scaleAlpha(b, 0.75f * f));
				if (g > 0) {
					GlowGeometry.flatSprite(pose, vc, cx(i, cam), cy(i, cam) + 0.004f, cz(i, cam), size * 1.08f, size * 1.08f, spin * 0.5f,
							GlowGeometry.scaleAlpha(b, 0.3f * g * f));
				}
			}
		});
	}

	/** Fast expansion that eases out. */
	private static float expand(float t) {
		float u = 1 - t;
		return 0.25f + 0.75f * (1 - u * u * u);
	}

	/** Quick fade-in, slow fade-out. */
	private static float fade(float t) {
		return Math.min(1f, t / 0.08f) * (1 - t) * (1 - t);
	}

	private float cx(int i, Vec3 cam) {
		return (float) (x[i] - cam.x);
	}

	private float cy(int i, Vec3 cam) {
		return (float) (y[i] - cam.y);
	}

	private float cz(int i, Vec3 cam) {
		return (float) (z[i] - cam.z);
	}
}
