package dev.elysium.visuals.client.util;

import java.util.Locale;

/** Helpers for packed ARGB colors (0xAARRGGBB). */
public final class ColorUtil {
	private ColorUtil() {
	}

	public static int alpha(int c) {
		return c >>> 24;
	}

	public static int red(int c) {
		return (c >> 16) & 0xFF;
	}

	public static int green(int c) {
		return (c >> 8) & 0xFF;
	}

	public static int blue(int c) {
		return c & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
	}

	public static int withAlpha(int c, int a) {
		return (clamp255(a) << 24) | (c & 0xFFFFFF);
	}

	/** Multiplies the alpha channel by {@code f} (0..1). */
	public static int mulAlpha(int c, float f) {
		return withAlpha(c, Math.round(alpha(c) * Math.max(0f, Math.min(1f, f))));
	}

	/** Linear interpolation of all four channels. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		return argb(
				Math.round(alpha(a) + (alpha(b) - alpha(a)) * t),
				Math.round(red(a) + (red(b) - red(a)) * t),
				Math.round(green(a) + (green(b) - green(a)) * t),
				Math.round(blue(a) + (blue(b) - blue(a)) * t));
	}

	/** Mixes only RGB, keeping the alpha of {@code a}. */
	public static int mixRgb(int a, int b, float t) {
		return withAlpha(mix(a, b, t), alpha(a));
	}

	/** Relative luminance 0..1 (ignores alpha). */
	public static float luminance(int c) {
		return (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f;
	}

	/** h, s, v in 0..1; returns opaque RGB. */
	public static int hsvToRgb(float h, float s, float v) {
		h = (h % 1f + 1f) % 1f * 6f;
		int i = (int) Math.floor(h);
		float f = h - i;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return argb(255, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
	}

	/** Returns {h, s, v} in 0..1. */
	public static float[] rgbToHsv(int c) {
		float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / d) / 6f;
		} else if (max == g) {
			h = ((b - r) / d + 2f) / 6f;
		} else {
			h = ((r - g) / d + 4f) / 6f;
		}
		h = (h % 1f + 1f) % 1f;
		float s = max == 0 ? 0 : d / max;
		return new float[]{h, s, max};
	}

	/** "#RRGGBB" for opaque colors, "#AARRGGBB" otherwise. */
	public static String toHex(int c) {
		if (alpha(c) == 0xFF) {
			return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
		}
		return String.format(Locale.ROOT, "#%08X", c);
	}

	/**
	 * Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (the '#' is optional).
	 *
	 * @return the color, or {@code null} if the string is not a valid HEX color
	 */
	public static Integer parseHex(String s) {
		if (s == null) {
			return null;
		}
		s = s.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (!s.matches("[0-9a-fA-F]+")) {
			return null;
		}
		try {
			return switch (s.length()) {
				case 3 -> {
					int r = Integer.parseInt(s.substring(0, 1), 16);
					int g = Integer.parseInt(s.substring(1, 2), 16);
					int b = Integer.parseInt(s.substring(2, 3), 16);
					yield argb(255, r * 17, g * 17, b * 17);
				}
				case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
				case 8 -> (int) Long.parseLong(s, 16);
				default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
