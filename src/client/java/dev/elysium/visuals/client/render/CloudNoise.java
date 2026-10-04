package dev.elysium.visuals.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Tileable 3D cloud noise, 64³ texels, packed as an atlas of 8×8 slices
 * (each with a 1-texel wrapped border so linear filtering crosses tile edges
 * seamlessly). Channels: R = Perlin-Worley (cloud shapes), G/B/A = inverted
 * Worley octaves 8/16/32 (detail that erodes the edges).
 *
 * <p>Generated once on a background thread; uploaded on the render thread
 * when ready. Until then {@link #view()} is null and clouds aren't drawn.
 */
public final class CloudNoise {
	public static final int SIZE = 64;
	private static final int TILE = SIZE + 2;
	private static final int GRID = 8;
	public static final int ATLAS = TILE * GRID;

	private static CompletableFuture<NativeImage> pending;
	private static DynamicTexture texture;

	private CloudNoise() {
	}

	/** The atlas, starting generation on first call; null until it is uploaded. */
	public static GpuTextureView view() {
		if (texture != null) {
			return texture.getTextureView();
		}
		if (pending == null) {
			pending = CompletableFuture.supplyAsync(CloudNoise::generate);
		}
		if (pending.isDone()) {
			NativeImage image = pending.join();
			texture = new DynamicTexture(() -> ElysiumVisuals.MOD_ID + " cloud noise", image);
			return texture.getTextureView();
		}
		return null;
	}

	// --- generation ---------------------------------------------------------------

	private static NativeImage generate() {
		long start = System.nanoTime();
		Random random = new Random(0x5EEDC10DL);
		Perlin perlin = new Perlin(random);
		Worley w4 = new Worley(4, random), w8 = new Worley(8, random), w16 = new Worley(16, random), w32 = new Worley(32, random);

		NativeImage image = new NativeImage(ATLAS, ATLAS, false);
		float[] r = new float[SIZE * SIZE * SIZE];
		float[] g = new float[r.length], b = new float[r.length], a = new float[r.length];
		for (int z = 0; z < SIZE; z++) {
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					float u = x / (float) SIZE, v = y / (float) SIZE, w = z / (float) SIZE;
					// Perlin fbm, 4 octaves from 4 cells per period.
					float pf = 0, amp = 0.5f, norm = 0;
					for (int o = 0, cells = 4; o < 4; o++, cells *= 2, amp *= 0.5f) {
						pf += amp * perlin.noise(u * cells, v * cells, w * cells, cells);
						norm += amp;
					}
					pf = clamp01(pf / norm * 0.5f + 0.5f);
					float worley = w4.inv(u, v, w) * 0.625f + w8.inv(u, v, w) * 0.25f + w16.inv(u, v, w) * 0.125f;
					// Perlin-Worley: billowy Perlin, its gaps carved by Worley cells.
					float pw = clamp01(remap(pf, worley - 1f, 1f, 0f, 1f));
					int i = (z * SIZE + y) * SIZE + x;
					r[i] = pw;
					g[i] = w8.inv(u, v, w);
					b[i] = w16.inv(u, v, w);
					a[i] = w32.inv(u, v, w);
				}
			}
		}
		// Perlin-Worley comes out in a narrow band (about 0.59..0.75): stretch it to 0..1 so
		// coverage and shape thresholds mean what they say.
		float[] sorted = r.clone();
		java.util.Arrays.sort(sorted);
		float lo = sorted[sorted.length / 50], hi = sorted[sorted.length - 1 - sorted.length / 50];
		for (int i = 0; i < r.length; i++) {
			r[i] = clamp01((r[i] - lo) / (hi - lo));
		}
		// Atlas with a wrapped 1-texel border around every slice.
		for (int z = 0; z < SIZE; z++) {
			int ox = (z % GRID) * TILE, oy = (z / GRID) * TILE;
			for (int ty = 0; ty < TILE; ty++) {
				int y = Math.floorMod(ty - 1, SIZE);
				for (int tx = 0; tx < TILE; tx++) {
					int x = Math.floorMod(tx - 1, SIZE);
					int i = (z * SIZE + y) * SIZE + x;
					int abgr = (byte8(a[i]) << 24) | (byte8(b[i]) << 16) | (byte8(g[i]) << 8) | byte8(r[i]);
					image.setPixelABGR(ox + tx, oy + ty, abgr);
				}
			}
		}
		ElysiumVisuals.LOGGER.info("Cloud noise generated in {} ms", (System.nanoTime() - start) / 1_000_000);
		return image;
	}

	private static int byte8(float v) {
		return Math.round(clamp01(v) * 255f) & 0xFF;
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	private static float remap(float v, float lo, float hi, float a, float b) {
		return a + (v - lo) / (hi - lo) * (b - a);
	}

	/** Tileable gradient noise: wraps every {@code period} cells. */
	private static final class Perlin {
		private final int[] perm = new int[512];
		private static final int[][] GRAD = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
				{1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

		Perlin(Random random) {
			int[] p = new int[256];
			for (int i = 0; i < 256; i++) {
				p[i] = i;
			}
			for (int i = 255; i > 0; i--) {
				int j = random.nextInt(i + 1);
				int t = p[i];
				p[i] = p[j];
				p[j] = t;
			}
			for (int i = 0; i < 512; i++) {
				perm[i] = p[i & 255];
			}
		}

		private float grad(int ix, int iy, int iz, int period, float dx, float dy, float dz) {
			int h = perm[perm[perm[Math.floorMod(ix, period)] + Math.floorMod(iy, period)] + Math.floorMod(iz, period)] % 12;
			int[] gv = GRAD[h];
			return gv[0] * dx + gv[1] * dy + gv[2] * dz;
		}

		private static float fade(float t) {
			return t * t * t * (t * (t * 6 - 15) + 10);
		}

		private static float lerp(float a, float b, float t) {
			return a + (b - a) * t;
		}

		/** About -1..1. */
		float noise(float x, float y, float z, int period) {
			int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
			float fx = x - ix, fy = y - iy, fz = z - iz;
			float u = fade(fx), v = fade(fy), w = fade(fz);
			float x00 = lerp(grad(ix, iy, iz, period, fx, fy, fz), grad(ix + 1, iy, iz, period, fx - 1, fy, fz), u);
			float x10 = lerp(grad(ix, iy + 1, iz, period, fx, fy - 1, fz), grad(ix + 1, iy + 1, iz, period, fx - 1, fy - 1, fz), u);
			float x01 = lerp(grad(ix, iy, iz + 1, period, fx, fy, fz - 1), grad(ix + 1, iy, iz + 1, period, fx - 1, fy, fz - 1), u);
			float x11 = lerp(grad(ix, iy + 1, iz + 1, period, fx, fy - 1, fz - 1),
					grad(ix + 1, iy + 1, iz + 1, period, fx - 1, fy - 1, fz - 1), u);
			return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w);
		}
	}

	/** Tileable Worley (cellular) noise with one feature point per cell. */
	private static final class Worley {
		private final int cells;
		private final float[] points;

		Worley(int cells, Random random) {
			this.cells = cells;
			this.points = new float[cells * cells * cells * 3];
			for (int i = 0; i < points.length; i++) {
				points[i] = random.nextFloat();
			}
		}

		/** 1 at a feature point, falling to 0 a cell away. */
		float inv(float u, float v, float w) {
			float x = u * cells, y = v * cells, z = w * cells;
			int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
			float best = Float.MAX_VALUE;
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = cx + dx, ny = cy + dy, nz = cz + dz;
						int i = ((Math.floorMod(nz, cells) * cells + Math.floorMod(ny, cells)) * cells + Math.floorMod(nx, cells)) * 3;
						float px = nx + points[i] - x, py = ny + points[i + 1] - y, pz = nz + points[i + 2] - z;
						best = Math.min(best, px * px + py * py + pz * pz);
					}
				}
			}
			return clamp01(1f - (float) Math.sqrt(best));
		}
	}
}
