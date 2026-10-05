package dev.elysium.visuals.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A blocky pet model: parts made of colored boxes, each part turning around
 * its own pivot. Units are model pixels (1/16 block), the model stands on
 * y = 0 and looks towards +Z.
 */
public final class PetModel {
	public enum Part { BODY, HEAD, EAR_L, EAR_R, LEG_FL, LEG_FR, LEG_BL, LEG_BR, TAIL }

	/** A box from (x0, y0, z0) to (x1, y1, z1). */
	record Box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
	}

	static final class PartDef {
		final float px, py, pz;
		final List<Box> boxes = new ArrayList<>();

		PartDef(float px, float py, float pz) {
			this.px = px;
			this.py = py;
			this.pz = pz;
		}

		PartDef box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
			boxes.add(new Box(x0, y0, z0, x1, y1, z1, color));
			return this;
		}

		/** The box and its mirror across x = 0. */
		PartDef pair(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
			box(x0, y0, z0, x1, y1, z1, color);
			return box(-x1, y0, z0, -x0, y1, z1, color);
		}
	}

	final Map<Part, PartDef> parts = new EnumMap<>(Part.class);
	/** Where the hat sits on the head (model units). */
	final float hatX, hatY, hatZ;
	/** Height of the legs: how far the body drops when lying. */
	public final float legHeight;
	/** Rest angle of the tail (degrees, positive = up). */
	public final float tailRest;
	/** Back end of the body (z), the pivot for sitting. */
	final float rearZ;
	/** Top of the model, for the name plate. */
	final float height;

	PetModel(float hatX, float hatY, float hatZ, float legHeight, float tailRest, float rearZ, float height) {
		this.hatX = hatX;
		this.hatY = hatY;
		this.hatZ = hatZ;
		this.legHeight = legHeight;
		this.tailRest = tailRest;
		this.rearZ = rearZ;
		this.height = height;
	}

	PartDef part(Part p, float px, float py, float pz) {
		PartDef d = new PartDef(px, py, pz);
		parts.put(p, d);
		return d;
	}

	public float height() {
		return height;
	}

	/** Per-part rotation in degrees (pitch around X, yaw around Y, roll around Z). */
	public static final class Pose {
		final Map<Part, float[]> rot = new EnumMap<>(Part.class);
		/** Whole-model pitch around the rear pivot (sitting) and drop (lying), in model units. */
		public float sitPitch, drop;
		public float propeller;
		public boolean hat;

		public Pose() {
			for (Part p : Part.values()) {
				rot.put(p, new float[3]);
			}
		}

		public void set(Part p, float pitch, float yaw, float roll) {
			float[] r = rot.get(p);
			r[0] = pitch;
			r[1] = yaw;
			r[2] = roll;
		}
	}

	/**
	 * Emits the model. {@code ps} is already at the pet's feet, turned to its
	 * heading and scaled to blocks / 16. {@code light} 0..1 dims the colors.
	 */
	public void draw(PoseStack ps, VertexConsumer vc, Pose pose, float light, int hatColor, int hatColor2) {
		ps.pushPose();
		ps.translate(0, -pose.drop, 0);
		if (pose.sitPitch != 0) {
			ps.translate(0, 0, rearZ);
			ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pose.sitPitch));
			ps.translate(0, 0, -rearZ);
		}
		for (Map.Entry<Part, PartDef> e : parts.entrySet()) {
			PartDef d = e.getValue();
			float[] r = pose.rot.get(e.getKey());
			ps.pushPose();
			ps.translate(d.px, d.py, d.pz);
			if (r[1] != 0) {
				ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(r[1]));
			}
			if (r[0] != 0) {
				ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(r[0]));
			}
			if (r[2] != 0) {
				ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(r[2]));
			}
			ps.translate(-d.px, -d.py, -d.pz);
			for (Box b : d.boxes) {
				box(ps.last(), vc, b, light);
			}
			if (e.getKey() == Part.HEAD && pose.hat) {
				hat(ps, vc, pose.propeller, light, hatColor, hatColor2);
			}
			ps.popPose();
		}
		ps.popPose();
	}

	/** A propeller beanie: two-tone cap, a stick and two turning blades. */
	private void hat(PoseStack ps, VertexConsumer vc, float propeller, float light, int c1, int c2) {
		float x = hatX, y = hatY, z = hatZ;
		box(ps.last(), vc, new Box(x - 2.2f, y, z - 2.2f, x, y + 1.6f, z + 2.2f, c1), light);
		box(ps.last(), vc, new Box(x, y, z - 2.2f, x + 2.2f, y + 1.6f, z + 2.2f, c2), light);
		box(ps.last(), vc, new Box(x - 1.4f, y + 1.6f, z - 1.4f, x + 1.4f, y + 2.2f, z + 1.4f, c1), light);
		box(ps.last(), vc, new Box(x - 0.3f, y + 2.2f, z - 0.3f, x + 0.3f, y + 3.6f, z + 0.3f, 0xFF3A3A3A), light);
		ps.pushPose();
		ps.translate(x, y + 3.6f, z);
		ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(propeller));
		box(ps.last(), vc, new Box(-3.8f, 0, -0.6f, 3.8f, 0.35f, 0.6f, 0xFFF2C53D), light);
		box(ps.last(), vc, new Box(-0.6f, 0, -3.8f, 0.6f, 0.35f, 3.8f, 0xFFE0352B), light);
		box(ps.last(), vc, new Box(-0.45f, 0.35f, -0.45f, 0.45f, 0.75f, 0.45f, 0xFF3A3A3A), light);
		ps.popPose();
	}

	private static int shade(int color, float f) {
		int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * f));
		int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * f));
		int b = Math.min(255, Math.round((color & 0xFF) * f));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Six faces with fixed directional shading, like blocks. */
	private static void box(PoseStack.Pose pose, VertexConsumer vc, Box b, float light) {
		float x0 = b.x0, y0 = b.y0, z0 = b.z0, x1 = b.x1, y1 = b.y1, z1 = b.z1;
		int top = shade(b.color, light), bottom = shade(b.color, 0.5f * light);
		int ns = shade(b.color, 0.8f * light), ew = shade(b.color, 0.62f * light);
		quad(pose, vc, top, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(pose, vc, bottom, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, vc, ns, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, vc, ns, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(pose, vc, ew, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(pose, vc, ew, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, int c, float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.addVertex(pose, ax, ay, az).setColor(c);
		vc.addVertex(pose, bx, by, bz).setColor(c);
		vc.addVertex(pose, cx, cy, cz).setColor(c);
		vc.addVertex(pose, dx, dy, dz).setColor(c);
	}

	// ---------------------------------------------------------------------
	// The pets
	// ---------------------------------------------------------------------

	/** Black-and-tan dachshund: long body on short legs, floppy ears. */
	public static final PetModel DACHSHUND = dachshund();
	public static final PetModel CAT = cat();
	public static final PetModel FOX = fox();

	private static PetModel dachshund() {
		int black = 0xFF2A2422, tan = 0xFFB8693A, dark = 0xFF141010;
		PetModel m = new PetModel(0, 11, 8.75f, 3, 35, -7, 13);
		m.part(Part.BODY, 0, 5.5f, 0)
				.box(-2.5f, 3, -7, 2.5f, 8, 7, black)
				.box(-2, 2.8f, -5.5f, 2, 3.2f, 5.5f, tan)
				.box(-2, 3.3f, 7, 2, 6.8f, 7.6f, tan);
		m.part(Part.HEAD, 0, 8, 7.5f)
				.box(-2.5f, 6.5f, 6.5f, 2.5f, 11, 11, black)
				.box(-1.5f, 6.5f, 11, 1.5f, 8.8f, 14.5f, tan)
				.box(-0.7f, 8.1f, 14.5f, 0.7f, 9, 14.9f, dark)
				.pair(0.9f, 9.2f, 11, 2, 10.1f, 11.15f, dark)
				.pair(0.9f, 10.15f, 11, 2.1f, 10.6f, 11.15f, tan);
		m.part(Part.EAR_L, -2.9f, 10.5f, 8.5f).box(-3.4f, 6.2f, 7.4f, -2.5f, 10.6f, 9.8f, black);
		m.part(Part.EAR_R, 2.9f, 10.5f, 8.5f).box(2.5f, 6.2f, 7.4f, 3.4f, 10.6f, 9.8f, black);
		m.part(Part.LEG_FL, -1.6f, 3, 5).box(-2.6f, 0, 4, -0.6f, 3.2f, 6, tan);
		m.part(Part.LEG_FR, 1.6f, 3, 5).box(0.6f, 0, 4, 2.6f, 3.2f, 6, tan);
		m.part(Part.LEG_BL, -1.6f, 3, -5).box(-2.6f, 0, -6, -0.6f, 3.2f, -4, tan);
		m.part(Part.LEG_BR, 1.6f, 3, -5).box(0.6f, 0, -6, 2.6f, 3.2f, -4, tan);
		m.part(Part.TAIL, 0, 7, -7).box(-0.5f, 6.5f, -12, 0.5f, 7.5f, -6.8f, black);
		return m;
	}

	/** Orange tabby cat with white chest and paws. */
	private static PetModel cat() {
		int orange = 0xFFE8964A, white = 0xFFF5EFE6, stripe = 0xFFB86A2C, pink = 0xFFE89AA0, eye = 0xFF7FD34E;
		PetModel m = new PetModel(0, 10, 6, 4, 50, -4.5f, 12);
		m.part(Part.BODY, 0, 6, 0)
				.box(-2, 4, -4.5f, 2, 8, 4.5f, orange)
				.box(-2.05f, 6, -1, 2.05f, 8.05f, 0, stripe)
				.box(-2.05f, 6, -3.5f, 2.05f, 8.05f, -2.5f, stripe)
				.box(-1.5f, 4.5f, 4.5f, 1.5f, 7, 4.7f, white);
		m.part(Part.HEAD, 0, 7.5f, 4.5f)
				.box(-2.5f, 6, 4, 2.5f, 10, 8, orange)
				.box(-1.5f, 6, 8, 1.5f, 7.6f, 9, white)
				.box(-0.5f, 7.3f, 9, 0.5f, 7.9f, 9.15f, pink)
				.pair(0.9f, 8.2f, 8, 1.9f, 9.1f, 8.1f, eye)
				.box(-2.55f, 9, 5.5f, 2.55f, 9.6f, 6.5f, stripe);
		m.part(Part.EAR_L, -1.6f, 10, 5.6f).box(-2.4f, 10, 5, -0.8f, 11.6f, 6.2f, orange);
		m.part(Part.EAR_R, 1.6f, 10, 5.6f).box(0.8f, 10, 5, 2.4f, 11.6f, 6.2f, orange);
		m.part(Part.LEG_FL, -1.1f, 4, 3.3f).box(-1.8f, 0, 2.6f, -0.4f, 4, 4, orange).box(-1.85f, 0, 2.55f, -0.35f, 1, 4.05f, white);
		m.part(Part.LEG_FR, 1.1f, 4, 3.3f).box(0.4f, 0, 2.6f, 1.8f, 4, 4, orange).box(0.35f, 0, 2.55f, 1.85f, 1, 4.05f, white);
		m.part(Part.LEG_BL, -1.1f, 4, -3.3f).box(-1.8f, 0, -4, -0.4f, 4, -2.6f, orange).box(-1.85f, 0, -4.05f, -0.35f, 1, -2.55f, white);
		m.part(Part.LEG_BR, 1.1f, 4, -3.3f).box(0.4f, 0, -4, 1.8f, 4, -2.6f, orange).box(0.35f, 0, -4.05f, 1.85f, 1, -2.55f, white);
		m.part(Part.TAIL, 0, 7.5f, -4.5f)
				.box(-0.5f, 7, -11, 0.5f, 8, -4.3f, orange)
				.box(-0.55f, 6.95f, -11.05f, 0.55f, 8.05f, -9.5f, stripe);
		return m;
	}

	/** Fox cub: orange coat, white muzzle and tail tip, black stockings. */
	private static PetModel fox() {
		int orange = 0xFFE07B39, white = 0xFFF4EEE6, black = 0xFF2A2420;
		PetModel m = new PetModel(0, 10.5f, 5.75f, 4, -10, -5, 13);
		m.part(Part.BODY, 0, 6.5f, 0)
				.box(-2.5f, 4, -5, 2.5f, 8.5f, 4, orange)
				.box(-2, 3.9f, -3, 2, 4.3f, 3, white);
		m.part(Part.HEAD, 0, 8, 4)
				.box(-3, 6, 3.5f, 3, 10.5f, 8, orange)
				.box(-3.05f, 6, 6, 3.05f, 7.8f, 8.05f, white)
				.box(-1.5f, 6, 8, 1.5f, 7.8f, 10.5f, white)
				.box(-0.6f, 7.3f, 10.5f, 0.6f, 8.1f, 10.65f, black)
				.pair(1.2f, 8.6f, 8, 2.2f, 9.4f, 8.1f, black);
		m.part(Part.EAR_L, -2, 10.5f, 5.6f).box(-2.8f, 10.5f, 5, -1.2f, 12.3f, 6.2f, orange).box(-2.85f, 11.8f, 4.95f, -1.15f, 12.4f, 6.25f, black);
		m.part(Part.EAR_R, 2, 10.5f, 5.6f).box(1.2f, 10.5f, 5, 2.8f, 12.3f, 6.2f, orange).box(1.15f, 11.8f, 4.95f, 2.85f, 12.4f, 6.25f, black);
		m.part(Part.LEG_FL, -1.5f, 4, 2.5f).box(-2.2f, 0, 1.8f, -0.8f, 4.2f, 3.2f, black);
		m.part(Part.LEG_FR, 1.5f, 4, 2.5f).box(0.8f, 0, 1.8f, 2.2f, 4.2f, 3.2f, black);
		m.part(Part.LEG_BL, -1.5f, 4, -3.7f).box(-2.2f, 0, -4.4f, -0.8f, 4.2f, -3, black);
		m.part(Part.LEG_BR, 1.5f, 4, -3.7f).box(0.8f, 0, -4.4f, 2.2f, 4.2f, -3, black);
		m.part(Part.TAIL, 0, 7.5f, -5)
				.box(-1.5f, 5.8f, -13, 1.5f, 8.6f, -4.8f, orange)
				.box(-1.55f, 5.75f, -13.05f, 1.55f, 8.65f, -10.8f, white);
		return m;
	}
}
