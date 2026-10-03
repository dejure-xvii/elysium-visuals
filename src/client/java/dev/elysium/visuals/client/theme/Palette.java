package dev.elysium.visuals.client.theme;

import dev.elysium.visuals.client.util.ColorUtil;

/**
 * Concrete colors the GUI draws with, derived from a {@link Theme}.
 * {@code glass} is a 0..1 blend factor so theme switches can fade smoothly.
 *
 * <p>The derived colors follow the launcher's design tokens: glass surfaces and
 * borders are the text color at low opacity (white 5.5% / 11% on dark themes),
 * {@code accent2} is the lighter lilac accent, and the three {@code blob} colors
 * are the soft glow spots behind the window.
 */
public record Palette(
		int background,
		int accent,
		int text,
		int selectedTab,
		float glass,
		int sidebar,
		int surface,
		int surfaceHover,
		int border,
		int textDim,
		int textFaint,
		int accent2,
		int accentSoft,
		int highlight,
		int inset,
		int tooltip,
		int bgTop,
		int bgBottom,
		int blob1,
		int blob2,
		int blob3
) {
	/** Status colors from the launcher (--ok, --warn, --danger). */
	public static final int OK = 0xFF4ADE80;
	public static final int WARN = 0xFFFBBF24;
	public static final int DANGER = 0xFFFF5C7A;

	public static Palette of(Theme theme) {
		return derive(
				theme.color(ColorSlot.BACKGROUND),
				theme.color(ColorSlot.ACCENT),
				theme.color(ColorSlot.TEXT),
				theme.color(ColorSlot.SELECTED_TAB),
				theme.glass() ? 1f : 0f);
	}

	public static Palette derive(int background, int accent, int text, int selectedTab, float glass) {
		boolean darkBg = ColorUtil.luminance(background) < 0.5f;

		// Glass layers: the text color at the launcher's opacities.
		int sidebar = ColorUtil.withAlpha(text, 0x0C);
		int surface = ColorUtil.withAlpha(text, 0x0E);       // 5.5%
		int surfaceHover = ColorUtil.withAlpha(text, 0x1A);  // 10%
		int border = ColorUtil.withAlpha(text, 0x1C);        // 11%
		int highlight = ColorUtil.withAlpha(text, 0x38);     // 22%
		// Inputs sit in a darker well (rgba(0,0,0,.25)); on light themes a faint tint instead.
		int inset = darkBg ? 0x40000000 : ColorUtil.withAlpha(text, 0x10);

		int textDim = ColorUtil.mulAlpha(text, 0.62f);
		int textFaint = ColorUtil.mulAlpha(text, 0.38f);

		// #7C5CFF → #B48CFF: hue a little towards pink, less saturated.
		float[] hsv = ColorUtil.rgbToHsv(accent);
		int accent2 = ColorUtil.withAlpha(ColorUtil.hsvToRgb(hsv[0] + 0.05f, hsv[1] * 0.7f, Math.min(1f, hsv[2] * 1.05f + 0.05f)), 0xFF);
		int accentSoft = ColorUtil.withAlpha(accent, 0x29); // 16%

		// Glow spots: accent, a pinker (#C04DFF) and a bluer (#3A7BFF) neighbor.
		float s = Math.max(0.55f, hsv[1]);
		int blob1 = ColorUtil.withAlpha(accent, 0xFF);
		int blob2 = ColorUtil.hsvToRgb(hsv[0] + 0.085f, Math.min(1f, s * 1.1f), 1f);
		int blob3 = ColorUtil.hsvToRgb(hsv[0] - 0.09f, Math.min(1f, s * 1.2f), 1f);

		// Deep violet-blue window gradient.
		int bgTop = ColorUtil.mixRgb(background, blob1, darkBg ? 0.05f : 0.03f);
		int bgBottom = ColorUtil.mixRgb(background, blob3, darkBg ? 0.06f : 0.03f);

		int tooltip = ColorUtil.withAlpha(ColorUtil.mixRgb(darkBg ? background : 0xFF141222, text, darkBg ? 0.07f : 0f), 0xF2);

		return new Palette(background, accent, text, selectedTab, glass, sidebar, surface, surfaceHover, border,
				textDim, textFaint, accent2, accentSoft, highlight, inset, tooltip, bgTop, bgBottom, blob1, blob2, blob3);
	}

	/** Interpolates the base colors and re-derives everything else. */
	public static Palette lerp(Palette a, Palette b, float t) {
		return derive(
				ColorUtil.mix(a.background, b.background, t),
				ColorUtil.mix(a.accent, b.accent, t),
				ColorUtil.mix(a.text, b.text, t),
				ColorUtil.mix(a.selectedTab, b.selectedTab, t),
				a.glass + (b.glass - a.glass) * t);
	}

	public boolean isGlass() {
		return glass > 0.5f;
	}

	public boolean isDark() {
		return ColorUtil.luminance(background) < 0.5f;
	}
}
