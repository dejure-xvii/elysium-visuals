package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights the block you're looking at instead of the black vanilla outline:
 * an animated plasma fill and/or a glowing outline in the theme colors. The
 * frame glides from block to block, follows the block's real shape (slabs,
 * stairs…) and fades in and out.
 */
public class BlockOverlay extends Module {
	/** Faces are pushed out a hair so they don't flicker against the block. */
	private static final double INFLATE = 0.002;

	private final BooleanSetting fill = add(new BooleanSetting("fill", "Заливка плазмой", true));
	private final BooleanSetting outline = add(new BooleanSetting("outline", "Контур", true));
	private final BooleanSetting throughWalls = add(new BooleanSetting("through_walls", "Сквозь стены", false));
	private final NumberSetting opacity = add(new NumberSetting("opacity", "Прозрачность заливки", 0.35, 0.05, 1, 0.05));
	private final NumberSetting thickness = add(new NumberSetting("thickness", "Толщина контура", 2, 0.5, 6, 0.25));
	private final NumberSetting smoothness = add(new NumberSetting("smoothness", "Плавность", 0.5, 0, 1, 0.05));
	private final NumberSetting plasmaSpeed = add(new NumberSetting("plasma_speed", "Скорость плазмы", 1, 0.1, 4, 0.1, "x"))
			.visibleWhen(fill::isOn);
	private final NumberSetting plasmaScale = add(new NumberSetting("plasma_scale", "Масштаб плазмы", 1, 0.25, 4, 0.05, "x"))
			.visibleWhen(fill::isOn);

	/** Animated union box (what the frame shows while moving). */
	private double minX, minY, minZ, maxX, maxY, maxZ;
	/** Union box of the current target shape. */
	private double tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ;
	private boolean hasBox;
	private float visibility;
	/** Exact boxes of the targeted shape (world coordinates), reused every frame. */
	private final List<AABB> boxes = new ArrayList<>();
	private BlockPos lastPos;
	private BlockState lastState;
	private long lastFrameNs;
	private float plasmaTime;

	public BlockOverlay() {
		super("block_overlay", "BlockOverlay", "Подсветка блока под прицелом: плазма и контур цвета темы", Category.RENDER);
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, state) -> !isEnabled());
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	protected void onDisable() {
		hasBox = false;
		visibility = 0;
		lastPos = null;
	}

	/** Updates {@link #boxes} for the block under the crosshair; false if there is none. */
	private boolean findTarget(Minecraft mc) {
		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) {
			return false;
		}
		BlockPos pos = bhr.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		// Shapes only change with the block, so rebuild the list only then.
		if (!pos.equals(lastPos) || state != lastState) {
			VoxelShape shape = state.getShape(mc.level, pos);
			if (shape.isEmpty()) {
				return false;
			}
			boxes.clear();
			for (AABB box : shape.toAabbs()) {
				boxes.add(box.move(pos));
			}
			lastPos = pos.immutable();
			lastState = state;
		}
		return !boxes.isEmpty();
	}

	private void render(LevelRenderContext ctx) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled()) {
			return;
		}
		boolean target = findTarget(mc);
		visibility += ((target ? 1f : 0f) - visibility) * (1f - (float) Math.exp(-dt * 12f));
		if (target) {
			tMinX = tMinY = tMinZ = Double.MAX_VALUE;
			tMaxX = tMaxY = tMaxZ = -Double.MAX_VALUE;
			for (AABB b : boxes) {
				tMinX = Math.min(tMinX, b.minX);
				tMinY = Math.min(tMinY, b.minY);
				tMinZ = Math.min(tMinZ, b.minZ);
				tMaxX = Math.max(tMaxX, b.maxX);
				tMaxY = Math.max(tMaxY, b.maxY);
				tMaxZ = Math.max(tMaxZ, b.maxZ);
			}
			if (!hasBox || smoothness.get() < 0.01) {
				minX = tMinX; minY = tMinY; minZ = tMinZ; maxX = tMaxX; maxY = tMaxY; maxZ = tMaxZ;
				hasBox = true;
			} else {
				// Exponential glide: higher smoothness = slower, softer follow.
				double k = 1 - Math.exp(-dt * (40 - 34 * smoothness.get()));
				minX += (tMinX - minX) * k; minY += (tMinY - minY) * k; minZ += (tMinZ - minZ) * k;
				maxX += (tMaxX - maxX) * k; maxY += (tMaxY - maxY) * k; maxZ += (tMaxZ - maxZ) * k;
			}
		}
		if (visibility < 0.01f || !hasBox) {
			if (visibility < 0.01f) {
				hasBox = false;
			}
			return;
		}
		plasmaTime += dt * plasmaSpeed.floatValue();

		CameraRenderState camera = ctx.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		// While gliding draw the moving union box; once settled, the block's exact shape.
		boolean settled = target && isSettled();
		List<AABB> draw = settled ? boxes : List.of(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
		boolean xray = throughWalls.isOn();
		int a = ThemeColors.primary(), b = ThemeColors.secondary();
		float vis = visibility;
		PoseStack poseStack = ctx.poseStack();

		if (fill.isOn()) {
			int colorA = GlowGeometry.scaleAlpha(a, opacity.floatValue() * vis);
			float scale = plasmaScale.floatValue() * 2.2f, time = plasmaTime;
			ctx.submitNodeCollector().submitCustomGeometry(poseStack, WorldPipelines.plasma(xray), (pose, vc) -> {
				for (AABB box : draw) {
					fillBox(pose, vc, box.inflate(INFLATE), cam, colorA, b, scale, time);
				}
			});
		}
		if (outline.isOn()) {
			int lineA = GlowGeometry.scaleAlpha(a, vis), lineB = GlowGeometry.scaleAlpha(b, vis);
			double cx = (minX + maxX) / 2 - cam.x, cy = (minY + maxY) / 2 - cam.y, cz = (minZ + maxZ) / 2 - cam.z;
			// Constant on-screen thickness: scale with distance.
			float width = thickness.floatValue() * 0.0025f * (float) Math.max(1, Math.sqrt(cx * cx + cy * cy + cz * cz));
			ctx.submitNodeCollector().submitCustomGeometry(poseStack, WorldPipelines.solid(xray), (pose, vc) -> {
				for (AABB box : draw) {
					outlineBox(pose, vc, box.inflate(INFLATE * 2), cam, width, lineA, lineB);
				}
			});
		}
	}

	/** The animated box has arrived at the target. */
	private boolean isSettled() {
		double e = 0.003;
		return Math.abs(minX - tMinX) < e && Math.abs(minY - tMinY) < e && Math.abs(minZ - tMinZ) < e
				&& Math.abs(maxX - tMaxX) < e && Math.abs(maxY - tMaxY) < e && Math.abs(maxZ - tMaxZ) < e;
	}

	private static void fillBox(PoseStack.Pose pose, VertexConsumer vc, AABB box, Vec3 cam, int colorA, int colorB,
								float scale, float time) {
		float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);
		// Plasma coordinates come from world positions, so neighbouring faces line up.
		float wx0 = (float) box.minX * scale, wx1 = (float) box.maxX * scale;
		float wy0 = (float) box.minY * scale, wy1 = (float) box.maxY * scale;
		float wz0 = (float) box.minZ * scale, wz1 = (float) box.maxZ * scale;
		float nr = ColorUtil.red(colorB) / 127.5f - 1, ng = ColorUtil.green(colorB) / 127.5f - 1, nb = ColorUtil.blue(colorB) / 127.5f - 1;
		float t = time;
		// bottom / top (x, z)
		face(pose, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, wx0 + t, wz0 - t, wx1 + t, wz1 - t, colorA, nr, ng, nb, false);
		face(pose, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, wx0 + t, wz0 - t, wx1 + t, wz1 - t, colorA, nr, ng, nb, true);
		// north / south (x, y)
		face(pose, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, wx0 - t, wy0 + t, wx1 - t, wy1 + t, colorA, nr, ng, nb, true);
		face(pose, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, wx0 - t, wy0 + t, wx1 - t, wy1 + t, colorA, nr, ng, nb, false);
		// west / east (z, y)
		face(pose, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, wz0 + t, wy0 - t, wz1 + t, wy1 - t, colorA, nr, ng, nb, false);
		face(pose, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, wz0 + t, wy0 - t, wz1 + t, wy1 - t, colorA, nr, ng, nb, true);
	}

	/** One face; the plasma coordinates span (u0,v0)-(u1,v1). {@code swap} matches the vertex order to the corners. */
	private static void face(PoseStack.Pose pose, VertexConsumer vc,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 float u0, float v0, float u1, float v1, int color, float nr, float ng, float nb, boolean swap) {
		vc.addVertex(pose, ax, ay, az).setUv(u0, v0).setColor(color).setNormal(nr, ng, nb);
		vc.addVertex(pose, bx, by, bz).setUv(swap ? u0 : u1, swap ? v1 : v0).setColor(color).setNormal(nr, ng, nb);
		vc.addVertex(pose, cx, cy, cz).setUv(u1, v1).setColor(color).setNormal(nr, ng, nb);
		vc.addVertex(pose, dx, dy, dz).setUv(swap ? u1 : u0, swap ? v0 : v1).setColor(color).setNormal(nr, ng, nb);
	}

	private static void outlineBox(PoseStack.Pose pose, VertexConsumer vc, AABB box, Vec3 cam, float w, int colorA, int colorB) {
		float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);
		// Bottom ring, top ring, verticals; the gradient runs along each edge.
		GlowGeometry.glowLine(pose, vc, x0, y0, z0, x1, y0, z0, w, 1, colorA, colorB);
		GlowGeometry.glowLine(pose, vc, x1, y0, z0, x1, y0, z1, w, 1, colorB, colorA);
		GlowGeometry.glowLine(pose, vc, x1, y0, z1, x0, y0, z1, w, 1, colorA, colorB);
		GlowGeometry.glowLine(pose, vc, x0, y0, z1, x0, y0, z0, w, 1, colorB, colorA);
		GlowGeometry.glowLine(pose, vc, x0, y1, z0, x1, y1, z0, w, 1, colorB, colorA);
		GlowGeometry.glowLine(pose, vc, x1, y1, z0, x1, y1, z1, w, 1, colorA, colorB);
		GlowGeometry.glowLine(pose, vc, x1, y1, z1, x0, y1, z1, w, 1, colorB, colorA);
		GlowGeometry.glowLine(pose, vc, x0, y1, z1, x0, y1, z0, w, 1, colorA, colorB);
		GlowGeometry.glowLine(pose, vc, x0, y0, z0, x0, y1, z0, w, 1, colorA, colorB);
		GlowGeometry.glowLine(pose, vc, x1, y0, z0, x1, y1, z0, w, 1, colorB, colorA);
		GlowGeometry.glowLine(pose, vc, x1, y0, z1, x1, y1, z1, w, 1, colorA, colorB);
		GlowGeometry.glowLine(pose, vc, x0, y0, z1, x0, y1, z1, w, 1, colorB, colorA);
	}
}
