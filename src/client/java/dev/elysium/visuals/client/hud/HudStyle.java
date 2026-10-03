package dev.elysium.visuals.client.hud;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look of HUD elements, matching the launcher: glass cards on a dark
 * base (readable over any sky), a header with a small icon and an upper-case
 * letter-spaced caption ("MINECRAFT 26.2 · FABRIC"), status dots and thin
 * gradient bars. All colors come from the active theme.
 */
public final class HudStyle {
	public static final int PAD = 6;
	public static final int RADIUS = 7;
	public static final int TITLE_H = 15;

	private HudStyle() {
	}

	/** Card background in the active theme. */
	public static void panel(GuiGraphicsExtractor g, Palette p, int x, int y, int w, int h) {
		panel(g, p, x, y, w, h, RADIUS);
	}

	public static void panel(GuiGraphicsExtractor g, Palette p, float x, float y, float w, float h, float r) {
		RenderUtil.softGlow(g, x, y, w, h, r, 0x38000000, 6);
		// Base: the theme background (capped so the world still shows a little), with the window's gradient.
		int a = Math.min(ColorUtil.alpha(p.background()), 0xC8);
		if (p.glass() > 0f) {
			// Glass over a bright sky needs a darker base so the text stays readable.
			RenderUtil.withAlpha(p.glass(), () -> RenderUtil.roundedRect(g, x, y, w, h, r, 0x6A0A0E18));
		}
		RenderUtil.roundedGradient(g, x, y, w, h, r, ColorUtil.withAlpha(p.bgTop(), a), ColorUtil.withAlpha(p.bgBottom(), a));
		RenderUtil.glass(g, x, y, w, h, r, p, 0f, 0f);
	}

	/**
	 * Card header: a small accent icon, an upper-case letter-spaced caption and
	 * an optional caption on the right, over a faded hairline. Returns the y
	 * where the content starts.
	 */
	public static int header(GuiGraphicsExtractor g, Palette p, HudIcon icon, String caption, String right, int x, int y, int w) {
		int cx = x + PAD + 1;
		if (icon != null) {
			icon.draw(g, cx + 3.5f, y + 8f, p.accent2());
			cx += 12;
		} else {
			RenderUtil.glowDot(g, cx + 2, y + 8f, 4, p.accent2());
			cx += 8;
		}
		RenderUtil.caption(g, caption, cx, y + 5, p.textDim());
		if (right != null && !right.isEmpty()) {
			RenderUtil.caption(g, right, x + w - PAD - RenderUtil.captionWidth(right), y + 5, p.textFaint());
		}
		RenderUtil.fadedLine(g, x + PAD, y + TITLE_H, w - PAD * 2, RenderUtil.hairline(), p.border());
		return y + TITLE_H + 3;
	}

	public static int headerWidth(String caption, String right) {
		int w = PAD + 13 + RenderUtil.captionWidth(caption) + PAD;
		if (right != null && !right.isEmpty()) {
			w += 12 + RenderUtil.captionWidth(right);
		}
		return w;
	}

	/** Header without an icon (kept for older elements). */
	public static int title(GuiGraphicsExtractor g, Palette p, String text, String right, int x, int y, int w) {
		return header(g, p, null, text, right, x, y, w);
	}

	public static int titleWidth(String text, String right) {
		return headerWidth(text, right);
	}

	/** Status dot like the launcher's: green = on/online, accent = active. */
	public static void dot(GuiGraphicsExtractor g, float cx, float cy, int color) {
		RenderUtil.glowDot(g, cx, cy, 4, color);
	}

	/** Thin rounded bar; {@code fraction} 0..1. The accent gets the launcher's lilac → violet gradient. */
	public static void bar(GuiGraphicsExtractor g, Palette p, float x, float y, float w, float h, float fraction, int color) {
		RenderUtil.roundedRect(g, x, y, w, h, h / 2, ColorUtil.withAlpha(p.text(), 0x1A));
		if (fraction > 0.001f) {
			float fw = Math.max(h, w * Math.min(1f, fraction));
			int from = color == p.accent() ? p.accent2() : ColorUtil.mixRgb(color, 0xFFFFFFFF, 0.3f);
			RenderUtil.roundedHGradient(g, x, y, fw, h, h / 2, from, color);
		}
	}

	/** m:ss, or h:mm:ss for long durations. */
	public static String formatSeconds(int seconds) {
		if (seconds >= 3600) {
			return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
		}
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
