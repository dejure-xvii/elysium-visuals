package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleEngine;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.particle.WorldParticle;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Glowing sprite particles in the world: bursts on hits, trails behind
 * projectiles and the player, a totem shower and idle fireflies. Purely
 * client-side visuals.
 */
public class Particles extends Module {
	private static final double THROW_RADIUS = 64;
	private static final long TOTEM_MS = 2500;
	private static final int TOTEM_GREEN = 0xFF4CFF6A;
	private static final int TOTEM_YELLOW = 0xFFFFE14D;

	private final MultiSelectSetting spawnOn = add(new MultiSelectSetting("spawn_on", "Spawn On",
			List.of(
					option("attack", "Attack"),
					option("throw", "Throw"),
					option("totem", "Totem"),
					option("move", "Move"),
					option("idle", "Idle")),
			Set.of("attack", "throw", "totem", "idle")));
	private final ModeSetting type = add(new ModeSetting("type", "Particle Type",
			List.of(
					option("bloom", "Bloom"),
					option("star", "Star"),
					option("heart", "Heart"),
					option("dollar", "Dollar"),
					option("snowflake", "Snow"),
					option("firefly", "Firefly"),
					option("random", "Random")),
			"bloom"));
	private final NumberSetting speed = add(new NumberSetting("speed", "Speed", 1, 0.2, 3, 0.1, "x"));
	private final NumberSetting size = add(new NumberSetting("size", "Size", 1, 0.3, 3, 0.1, "x"));
	private final NumberSetting attackCount = add(new NumberSetting("attack_count", "Attack Count", 20, 4, 80, 1))
			.visibleWhen(() -> on("attack"));
	private final NumberSetting totemCount = add(new NumberSetting("totem_count", "Totem Count", 6, 1, 20, 1))
			.visibleWhen(() -> on("totem"));
	private final NumberSetting moveCount = add(new NumberSetting("move_count", "Move Count", 2, 1, 10, 1))
			.visibleWhen(() -> on("move"));
	private final NumberSetting throwCount = add(new NumberSetting("throw_count", "Throw Count", 2, 1, 10, 1))
			.visibleWhen(() -> on("throw"));
	private final NumberSetting idleCount = add(new NumberSetting("idle_count", "Idle Count", 40, 5, 200, 5))
			.visibleWhen(() -> on("idle"));
	private final NumberSetting idleRange = add(new NumberSetting("idle_range", "Idle Range", 8, 2, 24, 1, " бл."))
			.visibleWhen(() -> on("idle"));
	private final BooleanSetting rotation = add(new BooleanSetting("rotation", "Rotation", true));
	private final BooleanSetting glow = add(new BooleanSetting("glow", "Glow", true));
	private final BooleanSetting throughWalls = add(new BooleanSetting("through_walls", "Through Walls", false));
	private final BooleanSetting customColor = add(new BooleanSetting("custom_color", "Свой цвет (вместо цвета темы)", false));
	private final ColorSetting color = add(new ColorSetting("color", "Цвет частиц", 0xFF8FDBFF))
			.visibleWhen(customColor::isOn);

	private final ParticleEngine engine = new ParticleEngine();
	/** Entities that just popped a totem → when their shower ends. */
	private final Map<LivingEntity, Long> totems = new HashMap<>();
	private ClientLevel lastLevel;
	private long lastFrameNs;

	public Particles() {
		super("particles", "Particles", "Светящиеся частицы при ударах, бросках, тотемах и вокруг игрока", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (isEnabled() && level.isClientSide() && on("attack")) {
				burst(entity);
			}
			return InteractionResult.PASS;
		});
	}

	/** Live particle count (for tests and debugging). */
	public int particleCount() {
		return engine.size();
	}

	/** Entities currently showered after a totem pop (for tests and debugging). */
	public int activeTotems() {
		return totems.size();
	}

	private boolean on(String event) {
		return spawnOn.isSelected(event);
	}

	/** Called (via a mixin) when any living entity's totem of undying activates on the client. */
	public static void onTotem(LivingEntity entity) {
		Particles m = ModuleManager.get().find(Particles.class);
		if (m != null && m.isEnabled() && m.on("totem")) {
			m.totems.put(entity, Util.getMillis() + TOTEM_MS);
		}
	}

	@Override
	protected void onDisable() {
		engine.clear();
		totems.clear();
	}

	// --- Spawning (per tick) ------------------------------------------------

	private ParticleTexture texture() {
		List<ParticleTexture> shapes = ParticleTexture.PARTICLE_SHAPES;
		if (type.is("random")) {
			return shapes.get(engine.random().nextInt(shapes.size()));
		}
		for (ParticleTexture t : shapes) {
			if (type.is(t.id())) {
				return t;
			}
		}
		return ParticleTexture.BLOOM;
	}

	private float scale() {
		return size.floatValue();
	}

	private double sp() {
		return speed.get();
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return;
		}
		if (level != lastLevel) {
			// New world or dimension: old particles belong elsewhere.
			engine.clear();
			totems.clear();
			lastLevel = level;
		}
		if (on("throw")) {
			trails(level, player);
		}
		if (on("totem")) {
			totemShower();
		}
		if (on("move") && !mc.options.getCameraType().isFirstPerson()) {
			moveTrail(player);
		}
		if (on("idle")) {
			fireflies(player);
		}
	}

	private void burst(Entity target) {
		RandomSource r = engine.random();
		AABB box = target.getBoundingBox();
		double cx = (box.minX + box.maxX) / 2, cy = box.minY + target.getBbHeight() * 0.6, cz = (box.minZ + box.maxZ) / 2;
		for (int i = 0; i < attackCount.intValue(); i++) {
			// Random direction on a sphere, biased slightly upwards.
			double theta = r.nextDouble() * Math.PI * 2, u = r.nextDouble() * 2 - 1;
			double s = Math.sqrt(1 - u * u), v = (1.5 + r.nextDouble() * 3) * sp();
			WorldParticle p = engine.spawn(cx, cy, cz, Math.cos(theta) * s * v, (u * 0.8 + 0.4) * v, Math.sin(theta) * s * v,
					0.9f + r.nextFloat() * 0.7f, (0.07f + r.nextFloat() * 0.06f) * scale(), texture());
			if (p != null) {
				p.gravity = 2.5f;
				p.drag = 1.8f;
			}
		}
	}

	private void trails(ClientLevel level, LocalPlayer player) {
		RandomSource r = engine.random();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof Projectile) || e.distanceToSqr(player) > THROW_RADIUS * THROW_RADIUS) {
				continue;
			}
			double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
			// Only while flying: a stuck arrow or a resting pearl doesn't move.
			if (dx * dx + dy * dy + dz * dz < 0.0004) {
				continue;
			}
			int n = throwCount.intValue();
			for (int i = 0; i < n; i++) {
				// Spread along the path travelled this tick, so the trail is continuous.
				double t = (i + r.nextDouble()) / n;
				WorldParticle p = engine.spawn(e.xo + dx * t, e.yo + dy * t + e.getBbHeight() / 2, e.zo + dz * t,
						(r.nextDouble() - 0.5) * 0.6 * sp(), (r.nextDouble() - 0.5) * 0.6 * sp(), (r.nextDouble() - 0.5) * 0.6 * sp(),
						0.5f + r.nextFloat() * 0.4f, (0.05f + r.nextFloat() * 0.04f) * scale(), texture());
				if (p != null) {
					p.drag = 2.5f;
				}
			}
		}
	}

	private void totemShower() {
		long now = Util.getMillis();
		RandomSource r = engine.random();
		Iterator<Map.Entry<LivingEntity, Long>> it = totems.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Long> entry = it.next();
			LivingEntity e = entry.getKey();
			if (now > entry.getValue() || e.isRemoved()) {
				it.remove();
				continue;
			}
			for (int i = 0; i < totemCount.intValue(); i++) {
				double ang = r.nextDouble() * Math.PI * 2, rad = e.getBbWidth() * (0.4 + r.nextDouble() * 0.5);
				double out = (0.4 + r.nextDouble() * 0.8) * sp();
				WorldParticle p = engine.spawn(e.getX() + Math.cos(ang) * rad, e.getY() + r.nextDouble() * e.getBbHeight() * 1.1,
						e.getZ() + Math.sin(ang) * rad,
						Math.cos(ang) * out, (1.0 + r.nextDouble() * 1.5) * sp(), Math.sin(ang) * out,
						1.0f + r.nextFloat() * 0.6f, (0.06f + r.nextFloat() * 0.05f) * scale(), texture());
				if (p != null) {
					p.color = r.nextBoolean() ? TOTEM_GREEN : TOTEM_YELLOW;
					p.gravity = 2.2f;
				}
			}
		}
	}

	private void moveTrail(LocalPlayer player) {
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		if (dx * dx + dz * dz < 0.0009) {
			return;
		}
		RandomSource r = engine.random();
		for (int i = 0; i < moveCount.intValue(); i++) {
			double ang = r.nextDouble() * Math.PI * 2, rad = 0.2 + r.nextDouble() * 0.3;
			WorldParticle p = engine.spawn(player.getX() + Math.cos(ang) * rad, player.getY() + 0.1 + r.nextDouble() * 0.4,
					player.getZ() + Math.sin(ang) * rad,
					(r.nextDouble() - 0.5) * 0.3 * sp(), (0.3 + r.nextDouble() * 0.5) * sp(), (r.nextDouble() - 0.5) * 0.3 * sp(),
					0.8f + r.nextFloat() * 0.5f, (0.04f + r.nextFloat() * 0.04f) * scale(), texture());
			if (p != null) {
				p.drag = 1.2f;
			}
		}
	}

	private void fireflies(LocalPlayer player) {
		RandomSource r = engine.random();
		int missing = idleCount.intValue() - engine.count(true);
		double range = idleRange.get();
		// A few per tick, so they fade in gradually instead of popping up at once.
		for (int i = 0; i < Math.min(3, missing); i++) {
			double ang = r.nextDouble() * Math.PI * 2, dist = Math.sqrt(r.nextDouble()) * range;
			WorldParticle p = engine.spawn(player.getX() + Math.cos(ang) * dist, player.getY() + 0.3 + r.nextDouble() * 3,
					player.getZ() + Math.sin(ang) * dist,
					(r.nextDouble() - 0.5) * 0.3 * sp(), (r.nextDouble() - 0.5) * 0.2 * sp(), (r.nextDouble() - 0.5) * 0.3 * sp(),
					4f + r.nextFloat() * 4f, (0.04f + r.nextFloat() * 0.04f) * scale(), texture());
			if (p != null) {
				p.idle = true;
				p.drag = 0.4f;
				p.spin *= 0.4f;
			}
		}
	}

	// --- Rendering (per frame) ----------------------------------------------

	private void render(LevelRenderContext ctx) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return;
		}
		engine.update(mc.level, mc.isPaused() ? 0f : dt, rotation.isOn());
		CameraRenderState camera = ctx.levelState().cameraRenderState;
		if (engine.size() == 0 || camera == null || camera.pos == null) {
			return;
		}
		float time = Util.getMillis() / 1000f;
		int accent = ThemeManager.get().palette().accent();
		float[] hsv = ColorUtil.rgbToHsv(accent);
		int custom = color.argb();
		boolean useCustom = customColor.isOn();
		engine.render(ctx.submitNodeCollector(), ctx.poseStack(), camera.pos, camera.orientation,
				throughWalls.isOn(), glow.isOn(), p -> {
					if (p.color != 0) {
						return p.color;
					}
					if (useCustom) {
						return custom;
					}
					// Shimmering gradient around the theme accent: hue and brightness drift per particle.
					float wave = (float) Math.sin(time * 1.6f + p.phase * Math.PI * 2);
					float h = hsv[0] + wave * 0.07f;
					float s = Math.max(0.25f, hsv[1] * (0.8f + 0.2f * wave));
					float v = Math.min(1f, Math.max(0.6f, hsv[2]) + 0.15f * (1 - wave) / 2);
					return ColorUtil.hsvToRgb(h, s, v);
				});
	}
}
