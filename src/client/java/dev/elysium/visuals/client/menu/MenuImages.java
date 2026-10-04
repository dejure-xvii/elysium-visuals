package dev.elysium.visuals.client.menu;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Image decoding for the menu backgrounds (PNG and JPG through ImageIO) and
 * the equirectangular → cube map conversion for the panoramas.
 */
final class MenuImages {
	private MenuImages() {
	}

	/** Decoded ARGB pixels. */
	record Pixels(int[] argb, int width, int height) {
	}

	/** Decodes an image, reading only every {@code step}-th pixel (fast thumbnails). */
	static Pixels decode(InputStream in, int step) throws IOException {
		try (ImageInputStream stream = ImageIO.createImageInputStream(in)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				throw new IOException("Unsupported image format");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream, true, true);
				ImageReadParam param = reader.getDefaultReadParam();
				if (step > 1) {
					param.setSourceSubsampling(step, step, 0, 0);
				}
				BufferedImage image = reader.read(0, param);
				int w = image.getWidth(), h = image.getHeight();
				return new Pixels(image.getRGB(0, 0, w, h, null, 0, w), w, h);
			} finally {
				reader.dispose();
			}
		}
	}

	/** Box-downscales so that neither side exceeds the limits (keeps the aspect ratio). */
	static Pixels fit(Pixels src, int maxW, int maxH) {
		float s = Math.min(1f, Math.min(maxW / (float) src.width(), maxH / (float) src.height()));
		if (s >= 1f) {
			return src;
		}
		return resize(src, 0, 0, src.width(), src.height(), Math.max(1, Math.round(src.width() * s)), Math.max(1, Math.round(src.height() * s)));
	}

	/** Area-averaged resize of the region (x, y, w, h) to (tw, th). */
	static Pixels resize(Pixels src, int x, int y, int w, int h, int tw, int th) {
		int[] out = new int[tw * th];
		for (int ty = 0; ty < th; ty++) {
			int y0 = y + ty * h / th, y1 = Math.max(y0 + 1, y + (ty + 1) * h / th);
			for (int tx = 0; tx < tw; tx++) {
				int x0 = x + tx * w / tw, x1 = Math.max(x0 + 1, x + (tx + 1) * w / tw);
				long r = 0, g = 0, b = 0;
				int n = 0;
				for (int sy = y0; sy < y1; sy++) {
					int row = sy * src.width();
					for (int sx = x0; sx < x1; sx++) {
						int c = src.argb()[row + sx];
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
						n++;
					}
				}
				out[ty * tw + tx] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return new Pixels(out, tw, th);
	}

	static NativeImage toNative(Pixels p) {
		NativeImage image = new NativeImage(p.width(), p.height(), false);
		for (int y = 0; y < p.height(); y++) {
			for (int x = 0; x < p.width(); x++) {
				image.setPixel(x, y, p.argb()[y * p.width() + x] | 0xFF000000);
			}
		}
		return image;
	}

	/**
	 * Projects an equirectangular panorama onto the six faces of a cube, in the
	 * order and orientation of Minecraft's panorama screenshots (south, west,
	 * north, east, up, down), stacked vertically as {@code CubeMapTexture} does
	 * it: in the order of {@link #SLOTS}, each face flipped upside down.
	 */
	private static final int[] SLOTS = {1, 3, 5, 4, 0, 2};

	static NativeImage cubeStrip(Pixels eq, int size) {
		NativeImage strip = new NativeImage(size, size * 6, false);
		float[][] faces = {
				// forward, right, up
				{0, 0, 1, -1, 0, 0, 0, 1, 0},
				{-1, 0, 0, 0, 0, -1, 0, 1, 0},
				{0, 0, -1, 1, 0, 0, 0, 1, 0},
				{1, 0, 0, 0, 0, 1, 0, 1, 0},
				{0, 1, 0, -1, 0, 0, 0, 0, -1},
				{0, -1, 0, -1, 0, 0, 0, 0, 1},
		};
		for (int slot = 0; slot < 6; slot++) {
			float[] b = faces[SLOTS[slot]];
			for (int j = 0; j < size; j++) {
				float v = 2f * (j + 0.5f) / size - 1f;
				for (int i = 0; i < size; i++) {
					float u = 2f * (i + 0.5f) / size - 1f;
					float dx = b[0] + u * b[3] - v * b[6];
					float dy = b[1] + u * b[4] - v * b[7];
					float dz = b[2] + u * b[5] - v * b[8];
					float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
					// x = east, -z = north: turning right moves to the right in the photo.
					double lon = Math.atan2(dx, -dz);
					double lat = Math.acos(Math.max(-1f, Math.min(1f, dy / len)));
					float sx = (float) ((0.5 + lon / (2 * Math.PI)) * eq.width() - 0.5);
					float sy = (float) (lat / Math.PI * eq.height() - 0.5);
					strip.setPixel(i, slot * size + (size - 1 - j), sample(eq, sx, sy));
				}
			}
		}
		return strip;
	}

	/** Bilinear sample; wraps horizontally, clamps vertically. */
	private static int sample(Pixels p, float x, float y) {
		int w = p.width(), h = p.height();
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
		float fx = x - x0, fy = y - y0;
		int xa = Math.floorMod(x0, w), xb = Math.floorMod(x0 + 1, w);
		int ya = Math.max(0, Math.min(h - 1, y0)), yb = Math.max(0, Math.min(h - 1, y0 + 1));
		int[] a = p.argb();
		int c00 = a[ya * w + xa], c10 = a[ya * w + xb], c01 = a[yb * w + xa], c11 = a[yb * w + xb];
		int out = 0xFF000000;
		for (int shift = 0; shift <= 16; shift += 8) {
			float top = ((c00 >> shift) & 0xFF) * (1 - fx) + ((c10 >> shift) & 0xFF) * fx;
			float bottom = ((c01 >> shift) & 0xFF) * (1 - fx) + ((c11 >> shift) & 0xFF) * fx;
			out |= Math.round(top * (1 - fy) + bottom * fy) << shift;
		}
		return out;
	}
}
