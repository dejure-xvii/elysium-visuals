package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Predicts where flying arrows, thrown items (pearls, snowballs, potions…) and
 * dropped items will land, with the game's own gravity, air/water drag and
 * block/entity collisions. Paths are cached per entity and only simulated
 * again when the projectile's velocity stops matching the prediction.
 */
public class Predictions extends Module {
	private static final int MAX_STEPS = 300;
	private static final int MAX_TRACKED = 32;
	private static final double RANGE = 96;

	private final MultiSelectSetting show = add(new MultiSelectSetting("show", "Показывать",
			List.of(option("arrows", "Стрелы и трезубцы"), option("thrown", "Брошенные предметы"), option("items", "Выброшенные предметы")),
			Set.of("arrows", "thrown", "items")));
	private final NumberSetting width = add(new NumberSetting("width", "Толщина линии", 1, 0.3, 3, 0.05, "x"));
	private final BooleanSetting glow = add(new BooleanSetting("glow", "Свечение", true));
	private final BooleanSetting plate = add(new BooleanSetting("plate", "Плашка в точке падения", true));

	private enum Kind { ARROW, THROWN, ITEM }

	/** A simulated path: positions per tick (world coords) and the velocity expected at each tick. */
	private static final class Path {
		final double[] pos = new double[(MAX_STEPS + 1) * 3];
		final double[] vel = new double[(MAX_STEPS + 1) * 3];
		int steps;
		/** Ticks since the path was computed (index of the entity's current point). */
		int index;
		boolean landed;
		ItemStack icon = ItemStack.EMPTY;
		boolean seen;
	}

	private final Map<Integer, Path> paths = new HashMap<>();
	private final List<LivingEntity> candidates = new ArrayList<>();
	private final Matrix4f viewProj = new Matrix4f();
	private final Vector4f clip = new Vector4f();

	public Predictions() {
		super("predictions", "Predictions", "Траектория стрел, брошенных и выброшенных предметов", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	protected void onDisable() {
		paths.clear();
	}

	/** Paths currently predicted (for tests and debugging). */
	public int trackedCount() {
		return paths.size();
	}

	private Kind kindOf(Entity e) {
		if (e instanceof AbstractArrow) {
			return show.isSelected("arrows") ? Kind.ARROW : null;
		}
		if (e instanceof ThrowableItemProjectile) {
			return show.isSelected("thrown") ? Kind.THROWN : null;
		}
		if (e instanceof ItemEntity) {
			return show.isSelected("items") ? Kind.ITEM : null;
		}
		return null;
	}

	private static ItemStack iconOf(Entity e) {
		if (e instanceof ThrowableItemProjectile t) {
			return t.getItem();
		}
		if (e instanceof ItemEntity item) {
			return item.getItem();
		}
		if (e instanceof ThrownTrident) {
			return new ItemStack(Items.TRIDENT);
		}
		return new ItemStack(e instanceof SpectralArrow ? Items.SPECTRAL_ARROW : Items.ARROW);
	}

	// --- Simulation (per tick) ----------------------------------------------

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			paths.clear();
			return;
		}
		for (Path p : paths.values()) {
			p.seen = false;
		}
		int tracked = 0;
		for (Entity e : level.entitiesForRendering()) {
			Kind kind = kindOf(e);
			if (kind == null || tracked >= MAX_TRACKED || e.distanceToSqr(mc.player) > RANGE * RANGE) {
				continue;
			}
			Vec3 v = e.getDeltaMovement();
			// Only things still in flight: stuck arrows and resting items don't move.
			boolean moving = v.lengthSqr() > 1e-4 && (kind != Kind.ITEM || !e.onGround());
			if (!moving) {
				continue;
			}
			tracked++;
			Path p = paths.get(e.getId());
			if (p == null) {
				p = new Path();
				paths.put(e.getId(), p);
				simulate(level, e, kind, p);
			} else {
				p.index++;
				int i = Math.min(p.index, p.steps) * 3;
				double dx = v.x - p.vel[i], dy = v.y - p.vel[i + 1], dz = v.z - p.vel[i + 2];
				// The real motion no longer matches (bounce, hit, server correction): simulate again.
				if (p.index >= p.steps || dx * dx + dy * dy + dz * dz > 4e-4) {
					simulate(level, e, kind, p);
				}
			}
			p.seen = true;
		}
		paths.values().removeIf(p -> !p.seen);
	}

	private void simulate(ClientLevel level, Entity e, Kind kind, Path p) {
		p.index = 0;
		p.landed = false;
		p.icon = iconOf(e);
		Entity owner = e instanceof Projectile proj ? proj.getOwner() : null;
		candidates.clear();
		if (kind != Kind.ITEM) {
			for (Entity other : level.getEntities(e, e.getBoundingBox().inflate(64), o -> o instanceof LivingEntity && o.isAlive() && !o.isSpectator())) {
				if (other != owner) {
					candidates.add((LivingEntity) other);
				}
			}
		}
		double x = e.getX(), y = e.getY(), z = e.getZ();
		Vec3 v0 = e.getDeltaMovement();
		double vx = v0.x, vy = v0.y, vz = v0.z;
		double gravity = e.getGravity();
		double waterDrag = e instanceof ThrownTrident ? 0.99 : kind == Kind.ARROW ? 0.6 : 0.8;
		double airDrag = kind == Kind.ITEM ? 0.98 : 0.99;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		store(p, 0, x, y, z, vx, vy, vz);
		int step = 0;
		while (step < MAX_STEPS) {
			boolean inWater = level.getFluidState(probe.set(x, y, z)).is(FluidTags.WATER);
			if (kind == Kind.ITEM && inWater) {
				p.landed = true; // items float on water: that's where they end up
				break;
			}
			// Each kind updates in the same order as its tick() in the game.
			if (kind == Kind.THROWN) {
				vy -= gravity;
				double d = inWater ? waterDrag : airDrag;
				vx *= d; vy *= d; vz *= d;
			} else if (kind == Kind.ITEM) {
				vy -= gravity;
			} else if (inWater) {
				vx *= waterDrag; vy *= waterDrag; vz *= waterDrag;
			}
			double nx = x + vx, ny = y + vy, nz = z + vz;
			Vec3 from = new Vec3(x, y, z), to = new Vec3(nx, ny, nz);
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
			Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : to;
			boolean hit = block.getType() != HitResult.Type.MISS;
			for (LivingEntity target : candidates) {
				AABB box = target.getBoundingBox().inflate(0.3);
				var entityHit = box.clip(from, end);
				if (entityHit.isPresent()) {
					end = entityHit.get();
					hit = true;
				}
			}
			x = end.x; y = end.y; z = end.z;
			if (kind == Kind.ARROW) {
				if (!inWater) {
					vx *= airDrag; vy *= airDrag; vz *= airDrag;
				}
				vy -= gravity;
			} else if (kind == Kind.ITEM) {
				vx *= airDrag; vy *= airDrag; vz *= airDrag;
			}
			step++;
			store(p, step, x, y, z, vx, vy, vz);
			if (hit) {
				p.landed = true;
				break;
			}
		}
		p.steps = step;
	}

	private static void store(Path p, int step, double x, double y, double z, double vx, double vy, double vz) {
		int i = step * 3;
		p.pos[i] = x; p.pos[i + 1] = y; p.pos[i + 2] = z;
		p.vel[i] = vx; p.vel[i + 1] = vy; p.vel[i + 2] = vz;
	}

	// --- World rendering -----------------------------------------------------

	private void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || paths.isEmpty() || mc.level == null) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		float w = 0.035f * width.floatValue();
		float glowAmount = glow.isOn() ? 1f : 0f;
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), WorldPipelines.solid(false), (pose, vc) -> {
			for (Path p : paths.values()) {
				int start = Math.min(p.index, p.steps);
				int total = Math.max(1, p.steps - start);
				// Begin at the projectile's smooth on-screen position between ticks.
				double px = lerp(p, start, 0, partial), py = lerp(p, start, 1, partial), pz = lerp(p, start, 2, partial);
				for (int s = start + 1; s <= p.steps; s++) {
					int i = s * 3;
					float t0 = (float) (s - 1 - start) / total, t1 = (float) (s - start) / total;
					GlowGeometry.glowLine(pose, vc,
							(float) (px - cam.x), (float) (py - cam.y), (float) (pz - cam.z),
							(float) (p.pos[i] - cam.x), (float) (p.pos[i + 1] - cam.y), (float) (p.pos[i + 2] - cam.z),
							w, glowAmount, mix(a, b, t0), mix(a, b, t1));
					px = p.pos[i]; py = p.pos[i + 1]; pz = p.pos[i + 2];
				}
			}
		});
		// A small glowing ring where it lands.
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), ParticleRenderTypes.get(ParticleTexture.RING, false), (pose, vc) -> {
			for (Path p : paths.values()) {
				if (p.landed) {
					int i = p.steps * 3;
					GlowGeometry.flatSprite(pose, vc, (float) (p.pos[i] - cam.x), (float) (p.pos[i + 1] - cam.y) + 0.02f,
							(float) (p.pos[i + 2] - cam.z), 0.35f, 0.35f, 0, b);
				}
			}
		});
	}

	/** Position along the path between tick {@code step} and the next, for smooth motion. */
	private static double lerp(Path p, int step, int axis, float partial) {
		int i = step * 3 + axis;
		int j = Math.min(step + 1, p.steps) * 3 + axis;
		return p.pos[i] + (p.pos[j] - p.pos[i]) * partial;
	}

	private static int mix(int a, int b, float t) {
		return 0xFF000000 | ThemeColors.gradient(a, b, t * 0.5f);
	}

	// --- Landing plates (HUD) -------------------------------------------------

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (!plate.isOn() || paths.isEmpty() || mc.level == null) {
			return;
		}
		CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
		camera.projectionMatrix.mul(camera.viewRotationMatrix, viewProj);
		Palette pal = ThemeManager.get().palette();
		for (Path p : paths.values()) {
			if (!p.landed) {
				continue;
			}
			int i = p.steps * 3;
			clip.set((float) (p.pos[i] - camera.pos.x), (float) (p.pos[i + 1] - camera.pos.y + 0.5), (float) (p.pos[i + 2] - camera.pos.z), 1f);
			viewProj.transform(clip);
			if (clip.w <= 0.05f) {
				continue; // behind the camera
			}
			float sx = (clip.x / clip.w * 0.5f + 0.5f) * g.guiWidth();
			float sy = (0.5f - clip.y / clip.w * 0.5f) * g.guiHeight();
			float seconds = Math.max(0, p.steps - p.index - partialTick) / 20f;
			String text = String.format(Locale.ROOT, "%.1f сек", seconds);
			int w = 6 + 16 + 4 + RenderUtil.width(text) + 6, h = 20;
			int x = Math.round(sx - w / 2f), y = Math.round(sy - h / 2f);
			HudStyle.panel(g, pal, x, y, w, h);
			g.item(p.icon, x + 5, y + 2);
			RenderUtil.text(g, null, text, x + 26, y + 6, pal.text());
		}
	}
}
