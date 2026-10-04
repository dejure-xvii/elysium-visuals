package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/** Drawing pieces shared by the Interface styles. All colors derive from the active theme. */
final class SkinKit {
	static final int GOLD = 0xFFFBBF24;
	/** Warm start of the "Панели" health gradient. */
	static final int WARM = 0xFFF6C744;

	private SkinKit() {
	}

	/**
	 * Base of plates and cards: the theme background pushed well towards black.
	 * Themes with dark text (the light theme) keep their light background so
	 * the text stays readable.
	 */
	static int dark(Palette p, int alpha) {
		if (ColorUtil.luminance(p.text()) < 0.5f) {
			return ColorUtil.withAlpha(p.bgBottom(), alpha);
		}
		// Glass themes have a near-white background: darken it more.
		float toBlack = 0.65f + 0.22f * Math.min(1f, p.glass());
		return ColorUtil.withAlpha(ColorUtil.mixRgb(p.bgBottom(), 0xFF000000, toBlack), alpha);
	}

	/** A dark rounded plate with a soft shadow. */
	static void plate(GuiGraphicsExtractor g, Palette p, float x, float y, float w, float h, float r, int alpha) {
		RenderUtil.softGlow(g, x, y, w, h, r, 0x30000000, 4);
		RenderUtil.roundedRect(g, x, y, w, h, r, dark(p, alpha));
	}

	/** Thin outline used by cards. */
	static void outline(GuiGraphicsExtractor g, Palette p, float x, float y, float w, float h, float r) {
		RenderUtil.roundedOutline(g, x, y, w, h, r, 0, ColorUtil.withAlpha(p.text(), 0x18));
	}

	/** Pill-shaped background for values. */
	static void capsule(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		RenderUtil.roundedRect(g, x, y, w, h, h / 2f, color);
	}

	/** Text width in the regular face. */
	static int w(String s) {
		return RenderUtil.width(s, RenderUtil.Face.REGULAR);
	}

	static int wBold(String s) {
		return RenderUtil.width(s, RenderUtil.Face.BOLD);
	}

	static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		RenderUtil.text(g, s, RenderUtil.Face.REGULAR, x, y, color);
	}

	static void bold(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		RenderUtil.text(g, s, RenderUtil.Face.BOLD, x, y, color);
	}

	/** Text that NameProtect must leave alone (the own nick when allowed). */
	static void part(GuiGraphicsExtractor g, HudData.Part part, int x, int y, int color) {
		if (part.raw()) {
			RenderUtil.textRaw(g, part.value(), x, y, color);
		} else {
			text(g, part.value(), x, y, color);
		}
	}

	static int partWidth(HudData.Part part) {
		return part.raw() ? RenderUtil.widthRaw(part.value()) : w(part.value());
	}

	/** Three small dots ("⋯") centered at (cx, cy). */
	static void menuDots(GuiGraphicsExtractor g, float cx, float cy, int color) {
		for (int i = -1; i <= 1; i++) {
			RenderUtil.roundedRect(g, cx + i * 2.6f - 0.75f, cy - 0.75f, 1.5f, 1.5f, 0.75f, color);
		}
	}

	/** Small rounded resize grip in the bottom-right corner, inset from (right, bottom). */
	static void grip(GuiGraphicsExtractor g, float right, float bottom, int color) {
		float rc = 3.5f;
		float cx = right - 3.5f - rc, cy = bottom - 3.5f - rc;
		arc(g, cx, cy, rc, 1f, 0.25f, 0.5f, color);
	}

	/** Ring of radius {@code r}: faint track and the {@code fraction} part filled clockwise from the top. */
	static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float t, float fraction, int color, int track) {
		RenderUtil.roundedOutline(g, cx - r, cy - r, r * 2, r * 2, r, t, track);
		arc(g, cx, cy, r - t / 2, t, 0, Math.max(0f, Math.min(1f, fraction)), color);
	}

	/** Arc from fraction {@code from} to {@code to} (0 = top, clockwise) on a circle of radius {@code rc}. */
	static void arc(GuiGraphicsExtractor g, float cx, float cy, float rc, float t, float from, float to, int color) {
		if (to - from < 0.003f) {
			return;
		}
		int segments = Math.max(2, (int) Math.ceil((to - from) * 40));
		double prev = -Math.PI / 2 + from * Math.PI * 2;
		for (int i = 1; i <= segments; i++) {
			double a = -Math.PI / 2 + (from + (to - from) * i / segments) * Math.PI * 2;
			RenderUtil.stroke(g, cx + (float) Math.cos(prev) * rc, cy + (float) Math.sin(prev) * rc,
					cx + (float) Math.cos(a) * rc, cy + (float) Math.sin(a) * rc, t, color);
			prev = a;
		}
	}

	/**
	 * Health bar with a light trail that catches up after a hit and gold
	 * absorption on top; the fill goes {@code from} → {@code to} left to right.
	 */
	static void healthBar(GuiGraphicsExtractor g, HudData.Target t, float x, float y, float w, float h,
						  int from, int to, int track) {
		float r = h / 2f;
		RenderUtil.roundedRect(g, x, y, w, h, r, track);
		float hp = t.health(), tr = t.trail();
		if (tr > hp + 0.002f) {
			RenderUtil.roundedRect(g, x, y, Math.max(h, w * tr), h, r, ColorUtil.withAlpha(ColorUtil.mixRgb(to, 0xFFFFFFFF, 0.65f), 0x90));
		}
		if (hp > 0.002f) {
			RenderUtil.roundedHGradient(g, x, y, Math.max(h, w * hp), h, r, from, to);
		}
		float abs = t.absorption();
		if (abs > 0.004f) {
			RenderUtil.roundedHGradient(g, x, y, Math.max(h, w * Math.min(1f, abs)), h, r, 0xFFFFE9A8, GOLD);
		}
	}

	/** Health with one decimal; {@code comma} gives "20,0". */
	static String health(float v, boolean comma) {
		String s = String.format(Locale.ROOT, "%.1f", Math.max(0f, v));
		return comma ? s.replace('.', ',') : s;
	}

	/** Remaining time: "m:ss" (or "mm:ss" when {@code padded}), "h:mm:ss" for long ones. */
	static String clock(float seconds, boolean padded) {
		int s = (int) Math.ceil(seconds);
		if (s >= 3600) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
		}
		return String.format(Locale.ROOT, padded ? "%02d:%02d" : "%d:%02d", s / 60, s % 60);
	}

	/** Cooldown time: "1.5с" under ten seconds, "12с" above, "…" while unknown. */
	static String cooldown(float seconds) {
		if (Float.isNaN(seconds) || seconds < 0) {
			return "…";
		}
		return seconds < 10 ? String.format(Locale.ROOT, "%.1fс", seconds) : (int) Math.ceil(seconds) + "с";
	}

	/** Right-hand text of a row in the given time formats. */
	static String value(HudData.Kind kind, HudData.Row row, boolean padded, String infinite) {
		return switch (kind) {
			case BUFFS -> row.infinite() ? infinite : clock(row.seconds(), padded);
			case COOLDOWNS -> cooldown(row.seconds());
			default -> row.value();
		};
	}

	/** Color of a buff name: harmful effects are tinted red. */
	static int nameColor(Palette p, HudData.Kind kind, HudData.Row row) {
		if (kind == HudData.Kind.BUFFS && !row.on()) {
			return ColorUtil.mix(p.text(), 0xFFFF5C7A, 0.55f);
		}
		if (kind == HudData.Kind.BINDS && !row.on()) {
			return p.textDim();
		}
		return p.text();
	}

	/** Status dot color of a friend. */
	static int statusColor(HudData.Row row) {
		return row.on() ? 0xFF4ADE80 : 0xFFFF5C7A;
	}
}
