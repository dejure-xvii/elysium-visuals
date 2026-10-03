package dev.elysium.visuals.client.hud;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-line HUD plate made of text segments separated by small dots,
 * e.g. "Elysium Visuals · 120 fps · 24 ms · 14:05". Subclasses can put
 * something custom in front of the segments (see {@link #drawLead}).
 */
public abstract class LabelElement extends HudElement {
	/**
	 * A text segment; color 0 means the theme's text (dimmed unless bold), {@code bold} for
	 * titles, {@code raw} to draw it exactly as given (NameProtect leaves it alone).
	 */
	public record Segment(String text, int color, boolean bold, boolean raw) {
		public Segment(String text, int color, boolean bold) {
			this(text, color, bold, false);
		}

		public static Segment of(String text) {
			return new Segment(text, 0, false);
		}
	}

	private static final int HEIGHT = 18;
	private static final int SEP = 11;

	private List<Segment> segments = List.of();

	protected LabelElement(String id, String name, Anchor anchor) {
		super(id, name, anchor);
	}

	/** Segments to show this frame; empty segments are skipped. */
	protected abstract List<Segment> segments(boolean preview);

	/** Width of the custom part before the segments (0 = none). */
	protected int leadWidth() {
		return 0;
	}

	/** Draws the custom part before the segments at ({@code x}, {@code y}); the plate is {@link #height} tall. */
	protected void drawLead(GuiGraphicsExtractor g, Palette p, int x, int y) {
	}

	private static int widthOf(Segment s) {
		return s.bold() ? RenderUtil.widthBold(s.text()) : s.raw() ? RenderUtil.widthRaw(s.text()) : RenderUtil.width(s.text());
	}

	@Override
	protected void measure(boolean preview) {
		List<Segment> next = new ArrayList<>();
		for (Segment s : segments(preview)) {
			if (s != null && !s.text().isEmpty()) {
				next.add(s);
			}
		}
		// Keep the last content while fading out.
		if (!next.isEmpty() || leadWidth() > 0) {
			segments = next;
		}
		int lead = leadWidth();
		int w = HudStyle.PAD * 2 + lead;
		for (int i = 0; i < segments.size(); i++) {
			w += widthOf(segments.get(i)) + (i > 0 || lead > 0 ? SEP : 0);
		}
		width = w;
		height = HEIGHT;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		HudStyle.panel(g, p, x, y, width, height);
		int tx = x + HudStyle.PAD;
		int ty = y + (height - 8) / 2;
		int lead = leadWidth();
		if (lead > 0) {
			drawLead(g, p, tx, y);
			tx += lead;
		}
		for (int i = 0; i < segments.size(); i++) {
			Segment s = segments.get(i);
			if (i > 0 || lead > 0) {
				RenderUtil.roundedRect(g, tx + SEP / 2f - 1, y + height / 2f - 1, 2, 2, 1, p.textFaint());
				tx += SEP;
			}
			int color = s.color() != 0 ? s.color() : s.bold() ? p.text() : p.textDim();
			if (s.bold()) {
				RenderUtil.textBold(g, s.text(), tx, ty, color);
			} else if (s.raw()) {
				RenderUtil.textRaw(g, s.text(), tx, ty, color);
			} else {
				RenderUtil.text(g, s.text(), RenderUtil.Face.REGULAR, tx, ty, color);
			}
			tx += widthOf(s);
		}
	}
}
