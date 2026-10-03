package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleEngine;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.particle.WorldParticle;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * An effect when something you hit within the last 2.5 s dies: it crumbles into
 * glowing particles, gets struck by (visual) lightning, a pillar of light rises
 * from the ground, or a supernova shell expands. Client-side only.
 */
public class KillEffect extends Module {
	private static final long HIT_WINDOW_MS = 2500;
	private static final long REPEAT_MS = 1000;
	private static final int MAX_FX = 16;
	private static final float AURA_TIME = 1.8f, NOVA_TIME = 1.2f;

	private final ModeSetting mode = add(new ModeSetting("mode", "Режим",
			List.of(option("particles", "Частицы"), option("lightning", "Молния"), option("aura", "Сияние"), option("nova", "Сверхновая")),
			"particles"));
	private final NumberSetting count = add(new NumberSetting("count", "Количество", 300, 50, 800, 10)).visibleWhen(this::particles);
	private final NumberSetting size = add(new NumberSetting("size", "Размер", 1, 0.3, 3, 0.05, "x")).visibleWhen(this::particles);
	private final NumberSetting delay = add(new NumberSetting("delay", "Задержка", 0.4, 0, 1.5, 0.05, " с")).visibleWhen(this::particles);
	private final NumberSetting evaporate = add(new NumberSetting("evaporate", "Время испарения", 1.4, 0.3, 3, 0.05, " с"))
			.visibleWhen(this::particles);
	private final NumberSetting rise = add(new NumberSetting("rise", "Высота подъёма", 1.5, 0, 4, 0.05, " бл.")).visibleWhen(this::particles);
	private final NumberSetting chaos = add(new NumberSetting("chaos", "Хаос", 0.6, 0, 2, 0.05)).visibleWhen(this::particles);
	private final BooleanSetting throughWalls = add(new BooleanSetting("through_walls", "Сквозь стены", false)).visibleWhen(this::particles);
	private final ModeSetting colorMode = add(new ModeSetting("color_mode", "Цвет",
			List.of(option("rainbow", "Радуга"), option("theme", "Тема"), option("custom", "Свой")), "theme")).visibleWhen(this::particles);
	private final ColorSetting color1 = add(new ColorSetting("color1", "Свой цвет", 0xFF8FDBFF))
			.visibleWhen(() -> particles() && colorMode.is("custom"));
	private final BooleanSetting useSecond = add(new BooleanSetting("use_second", "Второй цвет", false))
			.visibleWhen(() -> particles() && colorMode.is("custom"));
	private final ColorSetting color2 = add(new ColorSetting("color2", "Второй цвет", 0xFFFF7AD9))
			.visibleWhen(() -> particles() && colorMode.is("custom") && useSecond.isOn());
	private final NumberSetting auraWidth = add(new NumberSetting("aura_width", "Ширина", 0.8, 0.2, 3, 0.05, " бл."))
			.visibleWhen(() -> mode.is("aura"));
	private final NumberSetting auraHeight = add(new NumberSetting("aura_height", "Высота", 6, 1, 20, 0.5, " бл."))
			.visibleWhen(() -> mode.is("aura"));

	private final ParticleEngine engine = new ParticleEngine();
	private final Map<LivingEntity, Long> triggered = new WeakHashMap<>();
	// Aura and supernova pools: position, start time, size.
	private final double[] fxX = new double[MAX_FX], fxY = new double[MAX_FX], fxZ = new double[MAX_FX];
	private final long[] fxStart = new long[MAX_FX];
	private final float[] fxSize = new float[MAX_FX];
	private final boolean[] fxNova = new boolean[MAX_FX], fxAlive = new boolean[MAX_FX];
	private int nextBoltId = -0x10000;
	private ClientLevel lastLevel;
	private long lastFrameNs;

	public KillEffect() {
		super("kill_effect", "KillEffect", "Эффект при смерти цели: частицы, молния, сияние, сверхновая", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	/** Live kill particles (for tests and debugging). */
	public int particleCount() {
		return engine.size();
	}

	private boolean particles() {
		return mode.is("particles");
	}

	@Override
	protected void onDisable() {
		engine.clear();
		java.util.Arrays.fill(fxAlive, false);
		triggered.clear();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level != lastLevel) {
			onDisable();
			lastLevel = mc.level;
		}
		if (mc.level == null) {
			return;
		}
		long now = Util.getMillis();
		for (Map.Entry<LivingEntity, Long> hit : TargetTracker.recentHits().entrySet()) {
			LivingEntity e = hit.getKey();
			if (now - hit.getValue() > HIT_WINDOW_MS || !e.isDeadOrDying() || e.level() != mc.level) {
				continue;
			}
			Long last = triggered.get(e);
			if (last != null && now - last < REPEAT_MS) {
				continue;
			}
			triggered.put(e, now);
			trigger(mc.level, e);
		}
	}

	private void trigger(ClientLevel level, LivingEntity e) {
		switch (mode.get()) {
			case "lightning" -> {
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setVisualOnly(true);
					bolt.snapTo(e.getX(), e.getY(), e.getZ());
					bolt.setId(nextBoltId--);
					level.addEntity(bolt);
				}
			}
			case "aura", "nova" -> addFx(e, mode.is("nova"));
			default -> crumble(e);
		}
	}

	private void addFx(LivingEntity e, boolean nova) {
		int slot = 0;
		long oldest = Long.MAX_VALUE;
		for (int i = 0; i < MAX_FX; i++) {
			if (!fxAlive[i]) {
				slot = i;
				break;
			}
			if (fxStart[i] < oldest) {
				oldest = fxStart[i];
				slot = i;
			}
		}
		fxX[slot] = e.getX();
		fxY[slot] = e.getY();
		fxZ[slot] = e.getZ();
		fxStart[slot] = Util.getMillis();
		fxSize[slot] = Math.max(e.getBbWidth(), e.getBbHeight() * 0.6f);
		fxNova[slot] = nova;
		fxAlive[slot] = true;
	}

	/** The body breaks into particles from the top down, which drift out, rise and fade. */
	private void crumble(LivingEntity e) {
		RandomSource r = engine.random();
		float w = e.getBbWidth(), h = e.getBbHeight();
		int n = count.intValue();
		float life = evaporate.floatValue(), d = delay.floatValue(), c = chaos.floatValue();
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		for (int i = 0; i < n; i++) {
			float fy = r.nextFloat();
			// Narrower at the head and feet, like a body.
			float spread = w * 0.5f * (0.55f + 0.45f * (float) Math.sin(fy * Math.PI));
			double ang = r.nextDouble() * Math.PI * 2;
			float rr = (float) Math.sqrt(r.nextFloat()) * spread;
			double px = e.getX() + Math.cos(ang) * rr, py = e.getY() + fy * h, pz = e.getZ() + Math.sin(ang) * rr;
			float out = 0.3f + c * r.nextFloat();
			WorldParticle p = engine.spawn(px, py, pz,
					Math.cos(ang) * out + (r.nextFloat() - 0.5f) * c, rise.get() / life * (0.8 + r.nextDouble() * 0.6) + (r.nextFloat() - 0.5f) * c * 0.5,
					Math.sin(ang) * out + (r.nextFloat() - 0.5f) * c,
					life * (0.7f + r.nextFloat() * 0.5f), (0.035f + r.nextFloat() * 0.03f) * size.floatValue(), ParticleTexture.BLOOM);
			if (p == null) {
				break;
			}
			p.drag = 1.1f;
			// Delay: the top dissolves first.
			p.age = -d * (1f - fy) - r.nextFloat() * 0.08f;
			p.color = color(fy, r.nextFloat(), a, b);
		}
	}

	private int color(float height, float random, int themeA, int themeB) {
		return switch (colorMode.get()) {
			case "rainbow" -> ColorUtil.hsvToRgb(height * 0.8f + random * 0.15f, 0.75f, 1f);
			case "custom" -> useSecond.isOn() ? ColorUtil.mixRgb(color1.argb(), color2.argb(), random) | 0xFF000000 : color1.argb();
			default -> ThemeColors.gradient(themeA, themeB, random) | 0xFF000000;
		};
	}

	// --- Rendering --------------------------------------------------------------

	private void render(LevelRenderContext ctx) {
		long nowNs = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (nowNs - lastFrameNs) / 1e9f);
		lastFrameNs = nowNs;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return;
		}
		CameraRenderState camera = ctx.levelState().cameraRenderState;
		engine.update(mc.level, mc.isPaused() ? 0 : dt, false);
		if (engine.size() > 0) {
			engine.render(ctx.submitNodeCollector(), ctx.poseStack(), camera.pos, camera.orientation, throughWalls.isOn(), true,
					p -> p.color);
		}
		renderFx(ctx, camera.pos);
	}

	private void renderFx(LevelRenderContext ctx, Vec3 cam) {
		long now = Util.getMillis();
		boolean any = false;
		for (int i = 0; i < MAX_FX; i++) {
			if (fxAlive[i]) {
				float t = (now - fxStart[i]) / 1000f / (fxNova[i] ? NOVA_TIME : AURA_TIME);
				if (t >= 1) {
					fxAlive[i] = false;
				} else {
					any = true;
				}
			}
		}
		if (!any) {
			return;
		}
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		float aw = auraWidth.floatValue(), ah = auraHeight.floatValue();
		PoseStack ps = ctx.poseStack();
		ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(false), (pose, vc) -> {
			for (int i = 0; i < MAX_FX; i++) {
				if (!fxAlive[i]) {
					continue;
				}
				float x = (float) (fxX[i] - cam.x), y = (float) (fxY[i] - cam.y), z = (float) (fxZ[i] - cam.z);
				if (fxNova[i]) {
					float t = (now - fxStart[i]) / 1000f / NOVA_TIME;
					float radius = fxSize[i] * (0.4f + 3.2f * (1 - (1 - t) * (1 - t)));
					sphere(pose, vc, x, y + fxSize[i] * 0.6f, z, radius, a, b, (1 - t) * (1 - t));
				} else {
					float t = (now - fxStart[i]) / 1000f / AURA_TIME;
					float env = Math.min(1, t / 0.15f) * Math.min(1, (1 - t) / 0.45f);
					pillar(pose, vc, x, y, z, aw * 2.6f, ah, GlowGeometry.scaleAlpha(a, 0.25f * env));
					pillar(pose, vc, x, y, z, aw, ah * (0.4f + 0.6f * Math.min(1, t * 4)), GlowGeometry.scaleAlpha(b, 0.8f * env));
				}
			}
		});
		ctx.submitNodeCollector().submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.RING, false), (pose, vc) -> {
			for (int i = 0; i < MAX_FX; i++) {
				if (!fxAlive[i]) {
					continue;
				}
				float x = (float) (fxX[i] - cam.x), y = (float) (fxY[i] - cam.y) + 0.03f, z = (float) (fxZ[i] - cam.z);
				float t = (now - fxStart[i]) / 1000f / (fxNova[i] ? NOVA_TIME : AURA_TIME);
				if (fxNova[i]) {
					float r = fxSize[i] * (0.5f + 4f * t);
					GlowGeometry.flatSprite(pose, vc, x, y, z, r, r, t * 2, GlowGeometry.scaleAlpha(b, (1 - t)));
				} else {
					float env = Math.min(1, t / 0.15f) * Math.min(1, (1 - t) / 0.45f);
					float r = aw * (1.6f + 0.4f * t);
					GlowGeometry.flatSprite(pose, vc, x, y, z, r, r, t * 3, GlowGeometry.scaleAlpha(a, env));
				}
			}
		});
		ctx.submitNodeCollector().submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, false), (pose, vc) -> {
			for (int i = 0; i < MAX_FX; i++) {
				if (!fxAlive[i] || fxNova[i]) {
					continue;
				}
				float t = (now - fxStart[i]) / 1000f / AURA_TIME;
				float env = Math.min(1, t / 0.15f) * Math.min(1, (1 - t) / 0.45f);
				float r = aw * 2.4f;
				GlowGeometry.flatSprite(pose, vc, (float) (fxX[i] - cam.x), (float) (fxY[i] - cam.y) + 0.02f, (float) (fxZ[i] - cam.z),
						r, r, 0, GlowGeometry.scaleAlpha(b, 0.6f * env));
			}
		});
	}

	/** A camera-facing vertical beam, bright at the bottom and fading to the top. */
	private static void pillar(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float w, float h, int color) {
		float len = (float) Math.sqrt(x * x + z * z);
		if (len < 1e-4f) {
			return;
		}
		float sx = -z / len * w * 0.5f, sz = x / len * w * 0.5f;
		int top = color & 0x00FFFFFF;
		vc.addVertex(pose, x - sx, y, z - sz).setColor(color);
		vc.addVertex(pose, x + sx, y, z + sz).setColor(color);
		vc.addVertex(pose, x + sx, y + h, z + sz).setColor(top);
		vc.addVertex(pose, x - sx, y + h, z - sz).setColor(top);
	}

	/** A glowing sphere shell: brightest at the silhouette (where we look along its surface). */
	private static void sphere(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float r, int a, int b, float alpha) {
		int rings = 12, segments = 20;
		for (int i = 0; i < rings; i++) {
			float t0 = (float) Math.PI * i / rings, t1 = (float) Math.PI * (i + 1) / rings;
			for (int j = 0; j < segments; j++) {
				float p0 = (float) Math.PI * 2 * j / segments, p1 = (float) Math.PI * 2 * (j + 1) / segments;
				vertex(pose, vc, cx, cy, cz, r, t0, p0, a, b, alpha);
				vertex(pose, vc, cx, cy, cz, r, t1, p0, a, b, alpha);
				vertex(pose, vc, cx, cy, cz, r, t1, p1, a, b, alpha);
				vertex(pose, vc, cx, cy, cz, r, t0, p1, a, b, alpha);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float r, float theta, float phi,
							   int a, int b, float alpha) {
		float nx = (float) (Math.sin(theta) * Math.cos(phi)), ny = (float) Math.cos(theta), nz = (float) (Math.sin(theta) * Math.sin(phi));
		float x = cx + nx * r, y = cy + ny * r, z = cz + nz * r;
		float len = (float) Math.sqrt(x * x + y * y + z * z);
		float facing = Math.abs((nx * x + ny * y + nz * z) / Math.max(len, 1e-4f));
		float rim = (float) Math.pow(1 - facing, 1.5);
		int color = ColorUtil.mixRgb(a, b, (ny + 1) * 0.5f) | 0xFF000000;
		vc.addVertex(pose, x, y, z).setColor(GlowGeometry.scaleAlpha(color, (0.08f + 0.9f * rim) * alpha));
	}
}
