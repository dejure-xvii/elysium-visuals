package dev.elysium.visuals.client.gui.render;

import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.module.impl.utils.NameProtect;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

import java.util.Locale;

/**
 * Drawing primitives for the ClickGUI and HUD: anti-aliased rounded
 * rectangles, outlines, gradients, glows, the Elysium surfaces (window, glass
 * cards, input wells, switches) and Inter text.
 *
 * <p>Shapes go through {@link GuiPipelines#ROUNDED_RECT}: each one is a single
 * quad and the rounding/anti-aliasing happens in the fragment shader.
 */
public final class RenderUtil {
	/*
	 * Font textures are sampled with NEAREST filtering, so a TTF glyph is only
	 * crisp when its texels map 1:1 to screen pixels. There is one font
	 * definition per GUI scale (font/inter*_x<N>.json, oversample = N) and the
	 * one matching the current scale is used.
	 */
	private static final int MAX_FONT_SCALE = 8;

	/** Inter cuts used by the GUI. */
	public enum Face {
		/** Medium 9: body text. */
		REGULAR("inter"),
		/** SemiBold 9: names, titles of plates. */
		BOLD("inter_semibold"),
		/** Bold 9: the "Elysium" word of the brand. */
		HEAVY("inter_bold"),
		/** Light 9: the "Visuals" word of the brand. */
		LIGHT("inter_light"),
		/** Bold 13: page titles (baseline 3 units lower than the 9-unit faces). */
		TITLE("inter_title"),
		/** SemiBold 7: small upper-case captions. */
		SMALL("inter_small");

		private final FontDescription[] fonts = new FontDescription[MAX_FONT_SCALE];

		Face(String name) {
			for (int i = 0; i < MAX_FONT_SCALE; i++) {
				fonts[i] = new FontDescription.Resource(ElysiumVisuals.id(name + "_x" + (i + 1)));
			}
		}
	}

	/** Global alpha multiplier, used for the open/close fade of the whole GUI. */
	private static float alphaMul = 1f;

	private RenderUtil() {
	}

	public static void setAlpha(float a) {
		alphaMul = Math.max(0f, Math.min(1f, a));
	}

	public static float alpha() {
		return alphaMul;
	}

	/** Runs {@code body} with the global alpha multiplied by {@code factor}. */
	public static void withAlpha(float factor, Runnable body) {
		float prev = alphaMul;
		alphaMul = prev * Math.max(0f, Math.min(1f, factor));
		try {
			if (alphaMul > 0.004f) {
				body.run();
			}
		} finally {
			alphaMul = prev;
		}
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** One physical pixel in GUI units. */
	public static float hairline() {
		return 1f / guiScale();
	}

	private static int a(int color) {
		return alphaMul >= 1f ? color : ColorUtil.mulAlpha(color, alphaMul);
	}

	// ---------------------------------------------------------------------
	// Shapes
	// ---------------------------------------------------------------------

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		shape(g, x, y, w, h, 0, 0, 0, color, color, color, color);
	}

	public static void roundedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
		shape(g, x, y, w, h, r, 0, 0, color, color, color, color);
	}

	/** Rounded rectangle with a vertical gradient. */
	public static void roundedGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int top, int bottom) {
		shape(g, x, y, w, h, r, 0, 0, top, bottom, bottom, top);
	}

	/** Rounded rectangle with a horizontal gradient. */
	public static void roundedHGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int left, int right) {
		shape(g, x, y, w, h, r, 0, 0, left, left, right, right);
	}

	/** Rounded rectangle with an individual color per corner. */
	public static void roundedCorners(GuiGraphicsExtractor g, float x, float y, float w, float h, float r,
									  int topLeft, int topRight, int bottomLeft, int bottomRight) {
		shape(g, x, y, w, h, r, 0, 0, topLeft, bottomLeft, bottomRight, topRight);
	}

	/**
	 * Rounded outline drawn inside the given bounds.
	 *
	 * @param thickness in GUI units; {@code <= 0} means a 1 physical pixel hairline
	 */
	public static void roundedOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thickness, int color) {
		float t = thickness <= 0 ? 1f / guiScale() : thickness;
		shape(g, x, y, w, h, r, t, 0, color, color, color, color);
	}

	/** Soft shadow / glow fading outwards from the shape's edge over {@code size} GUI units. */
	public static void softGlow(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color, int size) {
		shape(g, x, y, w, h, r, -size, size, color, color, color, color);
	}

	/** Horizontal gradient (left → right). */
	public static void horizontalGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, int left, int right) {
		shape(g, x, y, w, h, 0, 0, 0, left, left, right, right);
	}

	/** Rectangle with an individual color per corner (used for the HSV square). */
	public static void gradientRect(GuiGraphicsExtractor g, float x, float y, float w, float h,
									int topLeft, int topRight, int bottomLeft, int bottomRight) {
		shape(g, x, y, w, h, 0, 0, 0, topLeft, bottomLeft, bottomRight, topRight);
	}

	/** A highlight line that is brightest in the middle and fades at both ends. */
	public static void fadedLine(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		int clear = ColorUtil.withAlpha(color, 0);
		horizontalGradient(g, x, y, w / 2f, h, clear, color);
		horizontalGradient(g, x + w / 2f, y, w / 2f, h, color, clear);
	}

	/** A line segment with round caps. */
	public static void stroke(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float width, int color) {
		float len = (float) Math.hypot(x2 - x1, y2 - y1);
		g.pose().pushMatrix();
		g.pose().translate((x1 + x2) / 2, (y1 + y2) / 2);
		g.pose().rotate((float) Math.atan2(y2 - y1, x2 - x1));
		roundedRect(g, -len / 2 - width / 2, -width / 2, len + width, width, width / 2, color);
		g.pose().popMatrix();
	}

	/** "›" centered on (cx, cy), rotated clockwise by {@code turn} quarter turns (1 = pointing down). */
	public static void chevron(GuiGraphicsExtractor g, float cx, float cy, float turn, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) (turn * Math.PI / 2));
		stroke(g, -1.1f, -2.2f, 1.1f, 0f, 1.1f, color);
		stroke(g, 1.1f, 0f, -1.1f, 2.2f, 1.1f, color);
		g.pose().popMatrix();
	}

	/** Check mark centered on (cx, cy); {@code progress} 0..1 draws it partially (for animation). */
	public static void checkmark(GuiGraphicsExtractor g, float cx, float cy, float progress, int color) {
		if (progress <= 0.01f) {
			return;
		}
		float ax = cx - 2.4f, ay = cy + 0.1f, bx = cx - 0.8f, by = cy + 1.7f, ex = cx + 2.5f, ey = cy - 1.7f;
		float first = Math.min(1f, progress * 2f);
		stroke(g, ax, ay, ax + (bx - ax) * first, ay + (by - ay) * first, 1.2f, color);
		if (progress > 0.5f) {
			float second = (progress - 0.5f) * 2f;
			stroke(g, bx, by, bx + (ex - bx) * second, by + (ey - by) * second, 1.2f, color);
		}
	}

	/** "×" centered on (cx, cy). */
	public static void cross(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
		stroke(g, cx - size, cy - size, cx + size, cy + size, 1.1f, color);
		stroke(g, cx - size, cy + size, cx + size, cy - size, 1.1f, color);
	}

	/** Magnifier icon centered on (cx, cy). */
	public static void searchIcon(GuiGraphicsExtractor g, float cx, float cy, int color) {
		roundedOutline(g, cx - 3.6f, cy - 3.6f, 6, 6, 3, 1.1f, color);
		stroke(g, cx + 1.6f, cy + 1.6f, cx + 3.4f, cy + 3.4f, 1.2f, color);
	}

	/** Checkerboard used behind transparent color previews. */
	public static void checkerboard(GuiGraphicsExtractor g, int x, int y, int w, int h, int cell) {
		g.fill(x, y, x + w, y + h, a(0xFFFFFFFF));
		int dark = a(0xFFC8C8C8);
		for (int cy = 0; cy < h; cy += cell) {
			for (int cx = ((cy / cell) % 2) * cell; cx < w; cx += cell * 2) {
				g.fill(x + cx, y + cy, x + Math.min(w, cx + cell), y + Math.min(h, cy + cell), dark);
			}
		}
	}

	public static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		int c = a(color);
		if (ColorUtil.alpha(c) > 0) {
			g.fill(x1, y1, x2, y2, c);
		}
	}

	public static void fillGradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom) {
		g.fillGradient(x1, y1, x2, y2, a(top), a(bottom));
	}

	// ---------------------------------------------------------------------
	// Text (Inter)
	// ---------------------------------------------------------------------

	public static Component styled(String s, Face face) {
		int index = Math.min(MAX_FONT_SCALE, guiScale()) - 1;
		FontDescription font = face.fonts[index];
		return Component.literal(s).withStyle(style -> style.withFont(font));
	}

	public static Component styled(String s, boolean bold) {
		return styled(s, bold ? Face.BOLD : Face.REGULAR);
	}

	public static int width(String s, Face face) {
		return font().width(styled(s, face));
	}

	public static int width(String s) {
		return width(s, Face.REGULAR);
	}

	public static int widthBold(String s) {
		return width(s, Face.BOLD);
	}

	/** Shortens {@code s} with "…" so it fits into {@code maxWidth}. */
	public static String ellipsize(String s, int maxWidth, Face face) {
		if (width(s, face) <= maxWidth) {
			return s;
		}
		String dots = "…";
		for (int end = s.length() - 1; end > 0; end--) {
			String candidate = s.substring(0, end).stripTrailing() + dots;
			if (width(candidate, face) <= maxWidth) {
				return candidate;
			}
		}
		return dots;
	}

	public static String ellipsize(String s, int maxWidth, boolean bold) {
		return ellipsize(s, maxWidth, bold ? Face.BOLD : Face.REGULAR);
	}

	public static void text(GuiGraphicsExtractor g, String s, Face face, int x, int y, int color) {
		int c = a(color);
		// Text with an alpha this low is invisible anyway; skipping avoids artifacts.
		if (ColorUtil.alpha(c) > 4) {
			g.text(font(), styled(s, face), x, y, c, false);
		}
	}

	public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		text(g, s, Face.REGULAR, x, y, color);
	}

	public static void textBold(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		text(g, s, Face.BOLD, x, y, color);
	}

	/** Regular text drawn exactly as given: NameProtect does not replace names in it. */
	public static void textRaw(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		int c = a(color);
		if (ColorUtil.alpha(c) > 4) {
			g.text(font(), raw(s), x, y, c, false);
		}
	}

	/** Width of {@link #textRaw} text. */
	public static int widthRaw(String s) {
		return font().width(raw(s));
	}

	private static Component raw(String s) {
		return ((MutableComponent) styled(s, Face.REGULAR)).withStyle(style -> style.withInsertion(NameProtect.RAW));
	}

	public static void centeredText(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
		text(g, s, Face.REGULAR, cx - width(s) / 2, y, color);
	}

	/** Extra space between caption letters, in physical pixels (the launcher's letter-spacing). */
	private static int captionTracking() {
		return Math.max(1, Math.round(guiScale() * 0.6f));
	}

	/** Advance of one caption glyph in whole physical pixels. */
	private static int glyphPx(String ch, int scale) {
		return Math.round(font().getSplitter().stringWidth(styled(ch, Face.SMALL)) * scale);
	}

	/** Width of an upper-case, letter-spaced caption. */
	public static int captionWidth(String s) {
		String u = s.toUpperCase(Locale.ROOT);
		int scale = guiScale();
		int px = 0;
		for (int i = 0; i < u.length(); ) {
			int cp = u.codePointAt(i);
			px += glyphPx(new String(Character.toChars(cp)), scale) + (i > 0 ? captionTracking() : 0);
			i += Character.charCount(cp);
		}
		return (int) Math.ceil(px / (float) scale);
	}

	/**
	 * Small upper-case caption with letter spacing (like the launcher's
	 * "uppercase; letter-spacing: .12em" subtitles). Every letter lands on a
	 * whole physical pixel, so it stays crisp.
	 */
	public static void caption(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		int c = a(color);
		if (ColorUtil.alpha(c) <= 4) {
			return;
		}
		String u = s.toUpperCase(Locale.ROOT);
		int scale = guiScale();
		int tracking = captionTracking();
		int offsetPx = 0;
		Font font = font();
		for (int i = 0; i < u.length(); ) {
			int cp = u.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			g.pose().pushMatrix();
			g.pose().translate(offsetPx / (float) scale, 0);
			g.text(font, styled(ch, Face.SMALL), x, y, c, false);
			g.pose().popMatrix();
			// Advance in physical pixels: the glyph's own width plus the tracking.
			offsetPx += glyphPx(ch, scale) + tracking;
			i += Character.charCount(cp);
		}
	}

	/** The two-weight brand: "Elysium" bold in the text color, "Visuals" light in lilac. Returns the width. */
	public static int brand(GuiGraphicsExtractor g, Palette p, int x, int y) {
		text(g, "Elysium", Face.HEAVY, x, y, p.text());
		int w = width("Elysium", Face.HEAVY) + 3;
		text(g, "Visuals", Face.LIGHT, x + w, y, p.accent2());
		return w + width("Visuals", Face.LIGHT);
	}

	public static int brandWidth() {
		return width("Elysium", Face.HEAVY) + 3 + width("Visuals", Face.LIGHT);
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	// ---------------------------------------------------------------------
	// Elysium surfaces (launcher style)
	// ---------------------------------------------------------------------

	/**
	 * The main window: drop shadow, deep violet-blue gradient, slowly drifting
	 * blurred glow spots, a thin light rim and a specular line along the top.
	 */
	public static void window(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Palette p) {
		softGlow(g, x, y, w, h, r, 0x66000000, 16);
		if (p.glass() > 0f) {
			// Translucent themes: a dark tint under the glass keeps light text readable over a bright sky.
			withAlpha(p.glass(), () -> roundedGradient(g, x, y, w, h, r, 0x700C1020, 0x88080A16));
		}
		roundedGradient(g, x, y, w, h, r, p.bgTop(), p.bgBottom());

		// Glow spots, clipped to the window. Weaker on translucent themes so the world stays visible.
		float strength = ColorUtil.alpha(p.background()) / 255f * (p.isDark() ? 1f : 0.45f);
		if (strength > 0.05f) {
			double t = Util.getMillis() / 1000.0;
			float dx1 = (float) Math.sin(t / 26.0 * Math.PI) * w * 0.08f, dy1 = (float) Math.cos(t / 21.0 * Math.PI) * h * 0.08f;
			float dx2 = (float) Math.cos(t / 32.0 * Math.PI) * w * 0.07f, dy2 = (float) Math.sin(t / 27.0 * Math.PI) * h * 0.1f;
			float dx3 = (float) Math.sin(t / 38.0 * Math.PI) * w * 0.06f, dy3 = (float) Math.cos(t / 30.0 * Math.PI) * h * 0.06f;
			float big = Math.min(w, h);
			g.enableScissor((int) Math.ceil(x + 1), (int) Math.ceil(y + 1), (int) Math.floor(x + w - 1), (int) Math.floor(y + h - 1));
			blob(g, x + w * 0.08f + dx1, y + h * 0.05f + dy1, big * 0.55f, p.blob1(), 0.30f * strength);
			blob(g, x + w * 0.92f + dx2, y + h * 0.3f + dy2, big * 0.48f, p.blob2(), 0.20f * strength);
			blob(g, x + w * 0.55f + dx3, y + h * 1.02f + dy3, big * 0.6f, p.blob3(), 0.20f * strength);
			g.disableScissor();
		}

		roundedOutline(g, x, y, w, h, r, 0, ColorUtil.mulAlpha(p.border(), 0.8f));
		fadedLine(g, x + r, y + 0.5f, w - 2 * r, hairline(), p.highlight());
	}

	/** A soft radial glow spot centered on (cx, cy). */
	public static void blob(GuiGraphicsExtractor g, float cx, float cy, float radius, int color, float alpha) {
		int c = ColorUtil.withAlpha(color, Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
		// The glow is only drawn outside the shape, so the tiny core is filled too.
		roundedRect(g, cx - 1, cy - 1, 2, 2, 1, c);
		softGlow(g, cx - 1, cy - 1, 2, 2, 1, c, Math.max(4, Math.round(radius)));
	}

	/**
	 * Glass card: translucent fill that is lighter at the top, a thin light
	 * border and a highlight along the top edge (the launcher's {@code .glass}).
	 *
	 * @param hover  0..1 hover highlight
	 * @param active 0..1 accent tint (selected / enabled)
	 */
	public static void glass(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Palette p, float hover, float active) {
		int top = ColorUtil.mix(ColorUtil.withAlpha(p.text(), 0x16), ColorUtil.withAlpha(p.text(), 0x24), hover);
		int bottom = ColorUtil.mix(p.surface(), ColorUtil.withAlpha(p.text(), 0x18), hover);
		roundedGradient(g, x, y, w, h, r, top, bottom);
		if (active > 0.01f) {
			roundedGradient(g, x, y, w, h, r, ColorUtil.mulAlpha(p.accent(), 0.22f * active), ColorUtil.mulAlpha(p.accent(), 0.12f * active));
		}
		int border = ColorUtil.mix(p.border(), ColorUtil.withAlpha(p.text(), 0x2E), hover);
		border = ColorUtil.mix(border, ColorUtil.mulAlpha(p.accent2(), 0.4f), active);
		roundedOutline(g, x, y, w, h, r, 0, border);
		fadedLine(g, x + r, y + hairline(), w - 2 * r, hairline(), ColorUtil.mulAlpha(p.highlight(), 0.8f + 0.2f * hover));
	}

	/** Input well (dark inset like the launcher's inputs and segmented controls). */
	public static void well(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Palette p, float hover) {
		roundedRect(g, x, y, w, h, r, p.inset());
		if (hover > 0.01f) {
			roundedRect(g, x, y, w, h, r, ColorUtil.withAlpha(p.text(), Math.round(0x0C * hover)));
		}
		roundedOutline(g, x, y, w, h, r, 0, ColorUtil.mix(ColorUtil.mulAlpha(p.border(), 0.75f), p.border(), hover));
	}

	/** Focus ring around an input: accent border plus a soft accent halo ("box-shadow: 0 0 0 3px"). */
	public static void focusRing(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Palette p, float f) {
		if (f <= 0.01f) {
			return;
		}
		softGlow(g, x, y, w, h, r, ColorUtil.mulAlpha(p.accentSoft(), f), 2);
		roundedOutline(g, x, y, w, h, r, 0, ColorUtil.mulAlpha(p.accent2(), 0.6f * f));
	}

	/**
	 * On/off switch: a translucent track that fills with the accent, and a
	 * white knob with a small shadow.
	 *
	 * @param on 0..1 animated state
	 */
	public static void toggle(GuiGraphicsExtractor g, float x, float y, float w, float h, Palette p, float on, float hover) {
		float r = h / 2f;
		roundedRect(g, x, y, w, h, r, ColorUtil.withAlpha(p.text(), Math.round(0x1F + 0x0C * hover)));
		roundedOutline(g, x, y, w, h, r, 0, ColorUtil.mulAlpha(p.border(), 1f - on));
		if (on > 0.01f) {
			int c = ColorUtil.mulAlpha(p.accent(), on);
			roundedHGradient(g, x, y, w, h, r, ColorUtil.mulAlpha(ColorUtil.mix(p.accent(), p.accent2(), 0.35f), on), c);
			softGlow(g, x, y, w, h, r, ColorUtil.mulAlpha(p.accent(), 0.25f * on), 3);
		}
		float k = h - 3;
		float kx = x + 1.5f + on * (w - h);
		softGlow(g, kx, y + 1.5f, k, k, k / 2f, 0x40000000, 2);
		roundedRect(g, kx, y + 1.5f, k, k, k / 2f, 0xFFFFFFFF);
	}

	/** Accent dot with a soft glow (status dots of the launcher). */
	public static void glowDot(GuiGraphicsExtractor g, float cx, float cy, float d, int color) {
		softGlow(g, cx - d / 2f, cy - d / 2f, d, d, d / 2f, ColorUtil.mulAlpha(color, 0.45f), Math.max(2, Math.round(d)));
		roundedRect(g, cx - d / 2f, cy - d / 2f, d, d, d / 2f, color);
	}

	/**
	 * The launcher logo: a rounded square with the accent gradient, a white
	 * sheen and rim, and the "E" mark — drawn from logo.svg's geometry.
	 */
	public static void logo(GuiGraphicsExtractor g, float x, float y, float size, Palette p, boolean glow) {
		float r = size * 0.25f;
		int light = ColorUtil.withAlpha(ColorUtil.mixRgb(p.accent(), 0xFFFFFFFF, 0.25f), 0xFF);
		int mid = ColorUtil.withAlpha(p.accent(), 0xFF);
		int dark = ColorUtil.withAlpha(ColorUtil.mixRgb(p.accent(), 0xFF1A0A80, 0.35f), 0xFF);
		if (glow) {
			softGlow(g, x, y, size, size, r, ColorUtil.mulAlpha(p.accent(), 0.4f), Math.round(size * 0.45f));
		}
		roundedCorners(g, x, y, size, size, r, light, mid, mid, dark);
		roundedGradient(g, x, y, size, size * 0.5f, r, 0x66FFFFFF, 0x00FFFFFF);
		roundedOutline(g, x, y, size, size, r, 0, 0x55FFFFFF);
		// The "E": svg units (viewBox 64..960) mapped onto the square.
		float u = size / 896f;
		float ox = x - 64 * u, oy = y - 64 * u;
		float rr = Math.max(0.3f, 10 * u);
		roundedRect(g, ox + 376 * u, oy + 330 * u, 92 * u, 364 * u, rr, 0xFFFFFFFF);
		roundedRect(g, ox + 376 * u, oy + 330 * u, 288 * u, 80 * u, rr, 0xFFFFFFFF);
		roundedRect(g, ox + 376 * u, oy + 472 * u, 254 * u, 80 * u, rr, 0xFFFFFFFF);
		roundedRect(g, ox + 376 * u, oy + 614 * u, 288 * u, 80 * u, rr, 0xFFFFFFFF);
	}

	/** Dark tooltip bubble to the right of {@code x}, vertically centered on {@code cy}. */
	public static void tooltip(GuiGraphicsExtractor g, Palette p, String text, int x, int cy, float shown) {
		if (shown <= 0.01f) {
			return;
		}
		int w = width(text) + 10;
		int h = 15;
		int tx = x + Math.round((1f - shown) * -3f);
		int ty = cy - h / 2;
		withAlpha(shown, () -> {
			softGlow(g, tx, ty, w, h, 5, 0x50000000, 6);
			roundedRect(g, tx, ty, w, h, 5, p.tooltip());
			roundedOutline(g, tx, ty, w, h, 5, 0, p.border());
			// The bubble is dark on every theme, so its text is always light.
			text(g, text, Face.REGULAR, tx + 5, ty + 4, p.isDark() ? p.text() : 0xFFF3F1FF);
		});
	}

	// ---------------------------------------------------------------------
	// Legacy panel styles (kept for callers outside the GUI)
	// ---------------------------------------------------------------------

	/** A floating plate in the current style (edit bar, popups). */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Palette p) {
		softGlow(g, x, y, w, h, r, 0x50000000, 10);
		roundedGradient(g, x, y, w, h, r, p.bgTop(), p.bgBottom());
		glass(g, x, y, w, h, r, p, 0f, 0f);
	}

	/** A card/row surface in the current style; {@code color} tints it. */
	public static void surface(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color, Palette p) {
		roundedRect(g, x, y, w, h, r, color);
		roundedOutline(g, x, y, w, h, r, 0, p.border());
	}

	/** Smaller glass element (used by the theme previews). */
	public static void glassSurface(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int tint) {
		int ta = ColorUtil.alpha(tint);
		roundedGradient(g, x, y, w, h, r,
				ColorUtil.withAlpha(tint, Math.min(255, Math.round(ta * 1.4f))),
				ColorUtil.withAlpha(tint, Math.round(ta * 0.7f)));
		roundedOutline(g, x, y, w, h, r, 0, 0x40FFFFFF);
		fadedLine(g, x + r, y + 0.5f, w - 2 * r, 0.5f, 0x90FFFFFF);
	}

	// ---------------------------------------------------------------------
	// Submission
	// ---------------------------------------------------------------------

	/**
	 * @param mode   {@code > 0} outline thickness, {@code 0} filled, {@code < 0} glow softness
	 * @param expand how far the quad extends past the shape (for glows)
	 */
	private static void shape(GuiGraphicsExtractor g, float x, float y, float w, float h, float r,
							  float mode, float expand, int cTL, int cBL, int cBR, int cTR) {
		if (w <= 0 || h <= 0) {
			return;
		}
		cTL = a(cTL);
		cBL = a(cBL);
		cBR = a(cBR);
		cTR = a(cTR);
		if ((cTL | cBL | cBR | cTR) >>> 24 == 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));

		float x0 = x - expand, y0 = y - expand, x1 = x + w + expand, y1 = y + h + expand;
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle(
				(int) Math.floor(x0), (int) Math.floor(y0),
				(int) Math.ceil(x1 - Math.floor(x0)), (int) Math.ceil(y1 - Math.floor(y0)))
				.transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}

		g.guiRenderState.addGuiElement(new ShapeRenderState(
				pose, x0, y0, x1, y1,
				x + w / 2f, y + h / 2f,
				Math.round(w * 8f), Math.round(h * 8f), Math.round(r * 16f), Math.round(mode * 16f),
				cTL, cBL, cBR, cTR,
				scissor, bounds));
	}
}
