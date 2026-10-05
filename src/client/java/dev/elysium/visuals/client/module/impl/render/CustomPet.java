package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.StringSetting;
import dev.elysium.visuals.client.pet.PetModel;
import dev.elysium.visuals.client.pet.PetModel.Part;
import dev.elysium.visuals.client.render.ScreenProjector;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Random;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * A pet that follows you around. It exists only on this client: nothing is
 * sent to the server and other players don't see it. Movement is a small
 * simulation of its own (gravity, stepping, jumping, steering around walls).
 */
public class CustomPet extends Module {
	private final ModeSetting kind = add(new ModeSetting("kind", "Питомец",
			List.of(option("none", "Нет"), option("dachshund", "Такса"), option("cat", "Котик"), option("fox", "Лисёнок"), option("bus", "Бусик")), "dachshund"));
	private final NumberSetting size = add(new NumberSetting("size", "Размер", 1, 0.5, 2.5, 0.05, "x"));
	private final BooleanSetting propeller = add(new BooleanSetting("propeller", "Пропеллер", false));
	private final BooleanSetting showName = add(new BooleanSetting("show_name", "Имя над головой", true));
	private final StringSetting name = add(new StringSetting("name", "Имя", "Бублик", 24)).visibleWhen(showName::isOn);

	private static final double GRAVITY = 0.08, JUMP = 0.42;
	private static final double FOLLOW = 2.4, RUN_AT = 6, TELEPORT_AT = 24;

	// Simulation state (block coordinates, feet).
	private ClientLevel level;
	private double x, y, z, px, py, pz, vy;
	private boolean onGround, spawned;
	private float bodyYaw, prevBodyYaw, headYaw, prevHeadYaw;
	private float walk, prevWalk, speed, prevSpeed;
	private int idleTicks, stuckTicks, lookTicks, nextLook = 80;
	private float sitAmount, prevSitAmount, lieAmount, prevLieAmount;
	private float steer, prevSteer;
	private final Random random = new Random();
	private final ScreenProjector projector = new ScreenProjector();
	private double renderX, renderY, renderZ;
	private boolean rendered;

	public CustomPet() {
		super("custom_pet", "CustomPet", "Питомец, который ходит (или ездит) за вами (видите только вы)", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	private PetModel model() {
		return switch (kind.get()) {
			case "dachshund" -> PetModel.DACHSHUND;
			case "cat" -> PetModel.CAT;
			case "fox" -> PetModel.FOX;
			case "bus" -> PetModel.BUS;
			default -> null;
		};
	}

	/** Where the pet stands (feet), or null if it isn't out. For tests and debugging. */
	public Vec3 petPosition() {
		return spawned ? new Vec3(x, y, z) : null;
	}

	/** 0..1 how much it is sitting / lying down. */
	public float sitting() {
		return sitAmount;
	}

	public float lying() {
		return lieAmount;
	}

	private double scale() {
		return size.get() / 16.0;
	}

	@Override
	protected void onEnable() {
		spawned = false;
	}

	// ---------------------------------------------------------------------
	// Simulation
	// ---------------------------------------------------------------------

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || model() == null) {
			spawned = false;
			return;
		}
		if (!spawned || level != mc.level || horizontalDist(player) > TELEPORT_AT || Math.abs(player.getY() - y) > 16) {
			teleport(player, mc.level);
		}
		px = x;
		py = y;
		pz = z;
		prevBodyYaw = bodyYaw;
		prevHeadYaw = headYaw;
		prevWalk = walk;
		prevSpeed = speed;
		prevSitAmount = sitAmount;
		prevLieAmount = lieAmount;

		double dx = player.getX() - x, dz = player.getZ() - z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double moveX = 0, moveZ = 0;
		boolean wantJump = false;
		if (dist > FOLLOW) {
			double sp = dist > RUN_AT ? 0.3 : 0.16;
			// Slow down when arriving.
			sp = Math.min(sp, (dist - FOLLOW) * 0.5 + 0.04);
			float desired = (float) Math.atan2(dz, dx);
			// Steer around walls: try the direct way first, then wider and wider turns.
			float[] offsets = {0, 0.6f, -0.6f, 1.2f, -1.2f, 1.9f, -1.9f};
			boolean found = false;
			for (float off : offsets) {
				double a = desired + off;
				double nx = x + Math.cos(a) * Math.max(sp, 0.35), nz = z + Math.sin(a) * Math.max(sp, 0.35);
				double obstacle = obstacleHeight(nx, nz);
				if (obstacle <= 0.6 || (obstacle <= 1.25 && onGround)) {
					moveX = Math.cos(a) * sp;
					moveZ = Math.sin(a) * sp;
					wantJump = obstacle > 0.6;
					found = true;
					break;
				}
			}
			if (!found && onGround) {
				wantJump = true;
				moveX = dx / dist * sp * 0.5;
				moveZ = dz / dist * sp * 0.5;
			}
			// The player climbed up and is close: hop after them.
			if (player.getY() - y > 0.9 && dist < 4 && onGround) {
				wantJump = true;
			}
			idleTicks = 0;
		} else {
			idleTicks++;
		}

		// Horizontal move with collision against walls taller than a step.
		double nx = x + moveX, nz = z + moveZ;
		double obstacle = obstacleHeight(nx, nz);
		if (obstacle > 0.6 && !(wantJump && obstacle <= 1.25) && !(obstacle <= 1.25 && !onGround && vy > 0)) {
			if (obstacleHeight(nx, z) <= 0.6) {
				nz = z;
			} else if (obstacleHeight(x, nz) <= 0.6) {
				nx = x;
			} else {
				nx = x;
				nz = z;
			}
		}
		if (obstacle > 0 && obstacle <= 0.6 && onGround) {
			y += obstacle; // step up
		}
		double moved = Math.hypot(nx - x, nz - z);
		x = nx;
		z = nz;
		stuckTicks = dist > FOLLOW + 2 && moved < 0.01 ? stuckTicks + 1 : 0;
		if (stuckTicks > 60) {
			teleport(player, mc.level);
		}

		// Vertical: jump, gravity, water, ground.
		if (wantJump && onGround) {
			vy = JUMP;
			onGround = false;
		}
		boolean water = level.getFluidState(BlockPos.containing(x, y + 0.3, z)).is(FluidTags.WATER);
		if (water) {
			vy = Math.min(vy + 0.05, 0.12);
		} else {
			vy = (vy - GRAVITY) * 0.98;
		}
		double ground = groundBelow(x, y + Math.max(0, vy) + 0.55, z);
		double ny = y + vy;
		if (ny <= ground) {
			ny = ground;
			vy = 0;
			onGround = true;
		} else {
			onGround = false;
		}
		// Bump the head.
		if (vy > 0 && solidAt(x, ny + 0.6 * size.get(), z)) {
			vy = 0;
		}
		y = ny;

		// Animation state.
		speed = (float) moved;
		walk += (float) moved * 5.5f;
		if (moved > 0.01) {
			float target = (float) Math.toDegrees(Math.atan2(nz - pz, nx - px)) - 90f;
			bodyYaw += Mth.wrapDegrees(target - bodyYaw) * 0.35f;
		}
		// A van turns its front wheels into the turn.
		prevSteer = steer;
		float turn = moved > 0.01 ? Mth.clamp(Mth.wrapDegrees(bodyYaw - prevBodyYaw) * 5f, -30f, 30f) : 0f;
		steer += (turn - steer) * 0.3f;
		boolean resting = idleTicks > 25 && onGround;
		if (model().vehicle) {
			resting = false; // a van doesn't sit or lie down
		}
		sitAmount += ((resting && idleTicks < 260 ? 1 : 0) - sitAmount) * 0.15f;
		lieAmount += ((resting && idleTicks >= 260 ? 1 : 0) - lieAmount) * 0.08f;

		// Now and then turn the head to the player.
		if (lookTicks > 0) {
			lookTicks--;
		} else if (--nextLook <= 0) {
			lookTicks = 30 + random.nextInt(40);
			nextLook = 60 + random.nextInt(140);
		}
		float headTarget = 0;
		if (lookTicks > 0 || (idleTicks > 0 && dist < 4)) {
			float toPlayer = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
			headTarget = Mth.clamp(Mth.wrapDegrees(toPlayer - bodyYaw), -65, 65);
		}
		headYaw += (headTarget - headYaw) * 0.25f;
	}

	private double horizontalDist(LocalPlayer p) {
		return Math.hypot(p.getX() - x, p.getZ() - z);
	}

	private void teleport(LocalPlayer player, ClientLevel lvl) {
		level = lvl;
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		// Just behind the player.
		x = player.getX() + Mth.sin(yaw) * 1.5;
		z = player.getZ() - Mth.cos(yaw) * 1.5;
		if (obstacleHeight(x, z) > 0.6 || !spawned) {
			x = player.getX();
			z = player.getZ();
		}
		y = groundBelow(x, player.getY() + 0.5, z);
		if (player.getY() - y > 3) {
			y = player.getY();
		}
		px = x;
		py = y;
		pz = z;
		vy = 0;
		onGround = true;
		bodyYaw = prevBodyYaw = player.getYRot();
		stuckTicks = 0;
		spawned = true;
	}

	/** Top of the collision shape at a block, or NaN if it has none. */
	private double topAt(int bx, int by, int bz) {
		BlockPos pos = new BlockPos(bx, by, bz);
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return Double.NaN;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() ? Double.NaN : by + shape.max(Direction.Axis.Y);
	}

	private boolean solidAt(double wx, double wy, double wz) {
		int by = Mth.floor(wy);
		double top = topAt(Mth.floor(wx), by, Mth.floor(wz));
		return !Double.isNaN(top) && top > wy;
	}

	/** Highest walkable surface at or below {@code fromY}. */
	private double groundBelow(double wx, double fromY, double wz) {
		int bx = Mth.floor(wx), bz = Mth.floor(wz);
		for (int by = Mth.floor(fromY); by >= Mth.floor(fromY) - 24; by--) {
			double top = topAt(bx, by, bz);
			if (!Double.isNaN(top) && top <= fromY + 1e-3) {
				return top;
			}
		}
		return level.getMinY();
	}

	/** How high something in the way at (wx, wz) rises above the pet's feet (0 if nothing, 9 if a wall). */
	private double obstacleHeight(double wx, double wz) {
		int bx = Mth.floor(wx), bz = Mth.floor(wz);
		double bodyTop = y + Math.max(0.5, 0.8 * size.get());
		double highest = 0;
		for (int by = Mth.floor(y + 0.01); by <= Mth.floor(bodyTop + 1.3); by++) {
			double top = topAt(bx, by, bz);
			if (!Double.isNaN(top) && top > y + 1e-3) {
				highest = Math.max(highest, top - y);
			}
		}
		if (highest > 0) {
			// Room for the body on top of the obstacle?
			double feet = y + highest;
			for (int by = Mth.floor(feet + 0.01); by <= Mth.floor(feet + Math.max(0.5, 0.8 * size.get())); by++) {
				double top = topAt(bx, by, bz);
				if (!Double.isNaN(top) && top > feet + 1e-3) {
					return 9;
				}
			}
		}
		return highest;
	}

	// ---------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------

	private void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		PetModel model = model();
		rendered = false;
		if (!isEnabled() || model == null || !spawned || mc.level != level || mc.player == null) {
			return;
		}
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		renderX = Mth.lerp(pt, px, x);
		renderY = Mth.lerp(pt, py, y);
		renderZ = Mth.lerp(pt, pz, z);
		rendered = true;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		float yaw = Mth.rotLerp(pt, prevBodyYaw, bodyYaw);
		float head = Mth.lerp(pt, prevHeadYaw, headYaw);
		float walkPhase = Mth.lerp(pt, prevWalk, walk);
		float spd = Mth.lerp(pt, prevSpeed, speed);
		float sit = Mth.lerp(pt, prevSitAmount, sitAmount);
		float lie = Mth.lerp(pt, prevLieAmount, lieAmount);
		double time = (System.nanoTime() / 1e9);

		PetModel.Pose pose = model.vehicle ? vehiclePose(model, walkPhase, spd, Mth.lerp(pt, prevSteer, steer), time)
				: animalPose(model, mc, walkPhase, spd, sit, lie, head, time);
		pose.hat = propeller.isOn();
		pose.propeller = (float) ((time * 900) % 360);

		// Light at the pet: block or sky light, whichever is brighter.
		BlockPos lp = BlockPos.containing(renderX, renderY + 0.3, renderZ);
		int raw = level.getRawBrightness(lp, level.getSkyDarken());
		float light = 0.28f + 0.72f * raw / 15f;
		int hat1 = ThemeColors.primary() | 0xFF000000, hat2 = ThemeColors.secondary() | 0xFF000000;

		PoseStack ps = ctx.poseStack();
		ps.pushPose();
		ps.translate(renderX - cam.x, renderY - cam.y, renderZ - cam.z);
		ps.mulPose(Axis.YP.rotationDegrees(-yaw));
		float sc = (float) scale();
		ps.scale(sc, sc, sc);
		PoseStack local = new PoseStack();
		local.last().pose().set(ps.last().pose());
		local.last().normal().set(ps.last().normal());
		ps.popPose();
		ctx.submitNodeCollector().submitCustomGeometry(new PoseStack(), WorldPipelines.OPAQUE,
				(p, vc) -> model.draw(local, vc, pose, light, hat1, hat2));
	}

	/** The van: wheels roll with the distance driven, the front ones steer, the body sways a little. */
	private PetModel.Pose vehiclePose(PetModel model, float walkPhase, float spd, float steerDeg, double time) {
		PetModel.Pose pose = new PetModel.Pose();
		// walk grows by 5.5 per block driven; the wheel turns by distance / radius.
		double radiusBlocks = model.wheelRadius / 16.0;
		float roll = (float) Math.toDegrees(walkPhase / 5.5 / radiusBlocks);
		pose.set(Part.LEG_FL, roll, steerDeg, 0);
		pose.set(Part.LEG_FR, roll, steerDeg, 0);
		pose.set(Part.LEG_BL, roll, 0, 0);
		pose.set(Part.LEG_BR, roll, 0, 0);
		float moving = Math.min(1f, spd / 0.1f);
		// The body leans out of the turn and rocks on bumps while driving.
		float rock = (float) Math.sin(time * 9) * 0.6f * moving;
		pose.set(Part.BODY, rock * 0.5f, 0, -steerDeg * 0.12f + rock * 0.4f);
		pose.drop = (float) (Math.abs(Math.sin(time * 11)) * 0.25 * moving);
		return pose;
	}

	private PetModel.Pose animalPose(PetModel model, Minecraft mc, float walkPhase, float spd, float sit, float lie, float head, double time) {
		PetModel.Pose pose = new PetModel.Pose();
		float swing = Math.min(1f, spd / 0.16f) * (spd > 0.22f ? 48f : 34f);
		float s = Mth.sin(walkPhase);
		boolean air = !onGround;
		if (air) {
			pose.set(Part.LEG_FL, -45, 0, 0);
			pose.set(Part.LEG_FR, -45, 0, 0);
			pose.set(Part.LEG_BL, 45, 0, 0);
			pose.set(Part.LEG_BR, 45, 0, 0);
		} else {
			pose.set(Part.LEG_FL, s * swing, 0, 0);
			pose.set(Part.LEG_BR, s * swing, 0, 0);
			pose.set(Part.LEG_FR, -s * swing, 0, 0);
			pose.set(Part.LEG_BL, -s * swing, 0, 0);
		}
		// Sitting: front up around the rear, hind legs folded, front legs kept upright.
		pose.sitPitch = -28f * sit;
		if (sit > 0.01f) {
			pose.set(Part.LEG_BL, -80 * sit, 0, 0);
			pose.set(Part.LEG_BR, -80 * sit, 0, 0);
			pose.set(Part.LEG_FL, 28 * sit, 0, 0);
			pose.set(Part.LEG_FR, 28 * sit, 0, 0);
		}
		// Lying: body on the ground, legs stretched out.
		pose.drop = model.legHeight * 0.85f * lie;
		if (lie > 0.01f) {
			pose.set(Part.LEG_FL, -85 * lie, 0, 0);
			pose.set(Part.LEG_FR, -85 * lie, 0, 0);
			pose.set(Part.LEG_BL, 85 * lie, 0, 0);
			pose.set(Part.LEG_BR, 85 * lie, 0, 0);
		}
		// Head: looks at you now and then, bobs while walking.
		pose.set(Part.HEAD, (sit * 22f - lie * 8f) + Mth.cos(walkPhase * 2) * swing * 0.05f, -head, 0);
		pose.set(Part.EAR_L, Mth.cos(walkPhase) * swing * 0.25f, -head, 0);
		pose.set(Part.EAR_R, -Mth.cos(walkPhase) * swing * 0.25f, -head, 0);
		// Tail: wags, faster when you're close.
		boolean happy = Math.hypot(mc.player.getX() - renderX, mc.player.getZ() - renderZ) < 4;
		float wag = (float) Math.sin(time * (happy ? 14 : 5)) * (happy ? 32 : 14);
		pose.set(Part.TAIL, model.tailRest + (air ? 15 : 0), wag, 0);
		return pose;
	}

	/** The name over the pet: the van is "Бусик" while the name is still the default one. */
	private String shownName() {
		String n = name.get();
		return kind.is("bus") && n.equals(name.defaultValue()) ? "Бусик" : n;
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		PetModel model = model();
		Minecraft mc = Minecraft.getInstance();
		if (!rendered || model == null || !showName.isOn() || name.get().isBlank() || mc.player == null) {
			return;
		}
		if (mc.player.distanceToSqr(renderX, renderY, renderZ) > 32 * 32) {
			return;
		}
		projector.begin(g.guiWidth(), g.guiHeight());
		double top = renderY + (model.height() + (propeller.isOn() ? 5 : 0)) * scale() + 0.25;
		if (!projector.project(renderX, top, renderZ)) {
			return;
		}
		Palette p = ThemeManager.get().palette();
		String text = shownName();
		int w = RenderUtil.width(text, RenderUtil.Face.BOLD) + 12;
		float bx = projector.x - w / 2f, by = projector.y - 14;
		RenderUtil.roundedRect(g, bx, by, w, 13, 6, ColorUtil.withAlpha(p.bgBottom(), 0xB0));
		RenderUtil.roundedOutline(g, bx, by, w, 13, 6, 0, p.border());
		RenderUtil.text(g, text, RenderUtil.Face.BOLD, Math.round(bx + 6), Math.round(by + 3), p.text());
	}
}
