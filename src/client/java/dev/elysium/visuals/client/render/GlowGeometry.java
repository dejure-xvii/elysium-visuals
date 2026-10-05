package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Builds glowing geometry for {@link WorldPipelines#GLOW}. All coordinates are
 * relative to the camera (world position minus camera position), as the world
 * render pass expects.
 */
public final class GlowGeometry {
	private GlowGeometry() {
	}

	/**
	 * A line segment as a flat ribbon that always faces the camera.
	 *
	 * @param width ribbon width in blocks
	 */
	public static void line(PoseStack.Pose pose, VertexConsumer vc,
							float x1, float y1, float z1, float x2, float y2, float z2,
							float width, int color1, int color2) {
		float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		// Side vector: perpendicular to the segment and to the view ray to its middle.
		float mx = (x1 + x2) * 0.5f, my = (y1 + y2) * 0.5f, mz = (z1 + z2) * 0.5f;
		float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-6f) {
			return;
		}
		float k = width * 0.5f / len;
		sx *= k;
		sy *= k;
		sz *= k;
		vc.addVertex(pose, x1 - sx, y1 - sy, z1 - sz).setColor(color1);
		vc.addVertex(pose, x1 + sx, y1 + sy, z1 + sz).setColor(color1);
		vc.addVertex(pose, x2 + sx, y2 + sy, z2 + sz).setColor(color2);
		vc.addVertex(pose, x2 - sx, y2 - sy, z2 - sz).setColor(color2);
	}

	/** A glowing line: a wide faint halo plus a thin bright core. */
	public static void glowLine(PoseStack.Pose pose, VertexConsumer vc,
								float x1, float y1, float z1, float x2, float y2, float z2,
								float width, float glow, int color1, int color2) {
		if (glow > 0) {
			line(pose, vc, x1, y1, z1, x2, y2, z2, width * 4f, scaleAlpha(color1, 0.18f * glow), scaleAlpha(color2, 0.18f * glow));
		}
		line(pose, vc, x1, y1, z1, x2, y2, z2, width, color1, color2);
	}

	/**
	 * A textured quad (for the sprite render types) centred on (cx, cy, cz),
	 * spanning ±(ux, uy, uz) along U and ±(vx, vy, vz) along V.
	 */
	public static void sprite(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz,
							  float ux, float uy, float uz, float vx, float vy, float vz, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		vc.addVertex(pose, cx - ux - vx, cy - uy - vy, cz - uz - vz).setUv(0, 1).setColor(color);
		vc.addVertex(pose, cx + ux - vx, cy + uy - vy, cz + uz - vz).setUv(1, 1).setColor(color);
		vc.addVertex(pose, cx + ux + vx, cy + uy + vy, cz + uz + vz).setUv(1, 0).setColor(color);
		vc.addVertex(pose, cx - ux + vx, cy - uy + vy, cz - uz + vz).setUv(0, 0).setColor(color);
	}

	/** A sprite lying flat (horizontal), rotated by {@code angle} radians around the vertical axis. */
	public static void flatSprite(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz,
								  float halfW, float halfL, float angle, int color) {
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		sprite(pose, vc, cx, cy, cz, c * halfW, 0, s * halfW, -s * halfL, 0, c * halfL, color);
	}

	/** A camera-facing vertical quad (camera at the origin), bright at the bottom and fading out up high. */
	public static void pillar(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float w, float h, int color) {
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

	/** Argb with its alpha multiplied by {@code f} (0..1). */
	public static int scaleAlpha(int argb, float f) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, f)));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
