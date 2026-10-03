package dev.elysium.visuals.client.render;

import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;

/**
 * Effect colors derived from the active theme (also Liquid Glass and custom
 * themes): the accent and a second, hue-shifted companion for gradients.
 */
public final class ThemeColors {
	private ThemeColors() {
	}

	/** The theme accent, fully opaque. */
	public static int primary() {
		return ColorUtil.withAlpha(ThemeManager.get().palette().accent(), 0xFF);
	}

	/** A companion color: the accent's hue shifted, a bit brighter, for two-color gradients. */
	public static int secondary() {
		float[] hsv = ColorUtil.rgbToHsv(primary());
		return ColorUtil.hsvToRgb(hsv[0] + 0.13f, Math.min(1f, hsv[1] * 0.9f + 0.1f), Math.min(1f, hsv[2] * 0.9f + 0.2f));
	}

	/** A point on the primary → secondary → primary loop; {@code t} wraps every 1.0. */
	public static int gradient(float t) {
		float f = (float) (0.5 - 0.5 * Math.cos((t % 1f + 1f) % 1f * Math.PI * 2));
		return ColorUtil.mixRgb(primary(), secondary(), f);
	}

	/** Same with precomputed colors (avoids recomputing HSV per vertex). */
	public static int gradient(int a, int b, float t) {
		float f = (float) (0.5 - 0.5 * Math.cos((t % 1f + 1f) % 1f * Math.PI * 2));
		return ColorUtil.mixRgb(a, b, f);
	}
}
