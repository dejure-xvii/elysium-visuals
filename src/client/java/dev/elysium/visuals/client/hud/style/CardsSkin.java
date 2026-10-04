package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * "Карточки": every block is a dark window card — icon, title and "⋯" in the
 * header, a small grip in the bottom-right corner. Rows: accent icon, name,
 * muted type, value in a capsule (binds with an on/off dot, timers with a
 * ring of the remaining time); muted column captions at the bottom.
 */
final class CardsSkin implements Skin {
	private static final int R = 6;
	private static final int ALPHA = 0xE6;
	private static final int HEAD_H = 18;
	private static final int ROW_H = 15;
	private static final int FOOT_H = 12;
	private static final int CAP_H = 11;
	private static final int RING = 4;

	@Override
	public boolean keepsEmptyHeader() {
		return false;
	}

	private static String value(HudData.Kind kind, HudData.Row row) {
		return SkinKit.value(kind, row, false, "∞");
	}

	private static boolean timed(HudData.Kind kind) {
		return kind == HudData.Kind.BUFFS || kind == HudData.Kind.COOLDOWNS;
	}

	/** Capsule width, plus the ring next to it for timers. */
	private static int capsuleWidth(HudData.Kind kind, HudData.Row row) {
		String v = value(kind, row);
		int w = 7 + SkinKit.w(v) + 7;
		if (kind == HudData.Kind.BINDS || kind == HudData.Kind.FRIENDS) {
			w += 8;
		}
		return w;
	}

	private static int rightWidth(HudData.Kind kind, HudData.Row row) {
		return capsuleWidth(kind, row) + (timed(kind) ? 4 + RING * 2 : 0);
	}

	private static int smallWidth(String s) {
		return RenderUtil.width(s, RenderUtil.Face.SMALL);
	}

	private static int rowWidth(HudData.Kind kind, HudData.Row row) {
		int w = 22 + SkinKit.w(row.name());
		if (!row.sub().isEmpty()) {
			w += 4 + smallWidth(row.sub());
		}
		return w + 12 + rightWidth(kind, row) + 8;
	}

	private static int frameWidth(String title, String left, String right) {
		int header = 20 + SkinKit.w(title) + 20;
		int footer = 8 + smallWidth(left) + 16 + smallWidth(right) + 12;
		return Math.max(118, Math.max(header, footer));
	}

	@Override
	public Size measureList(HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows) {
		int w = frameWidth(block.title(), block.leftColumn(), block.rightColumn());
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			w = Math.max(w, rowWidth(block.kind(), r.value()));
		}
		return new Size(w, HEAD_H + 1 + Math.round(Skin.rowsHeight(rows, ROW_H)) + FOOT_H);
	}

	/** Card body, header and footer; returns the y where rows start. */
	private static int frame(GuiGraphicsExtractor g, Palette p, HudIcon icon, boolean logo, String title,
							 String left, String right, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		SkinKit.outline(g, p, x, y, w, h, R);
		if (logo) {
			RenderUtil.logo(g, x + 6, y + 5, 8, p, false);
		} else {
			icon.draw(g, x + 10, y + HEAD_H / 2f, p.accent2());
		}
		SkinKit.text(g, title, x + 19, y + 5, p.text());
		SkinKit.menuDots(g, x + w - 10, y + HEAD_H / 2f, p.textFaint());
		int fy = y + h - FOOT_H + 2;
		RenderUtil.text(g, left, RenderUtil.Face.SMALL, x + 8, fy, p.textFaint());
		RenderUtil.text(g, right, RenderUtil.Face.SMALL, x + w - 12 - smallWidth(right), fy, p.textFaint());
		SkinKit.grip(g, x + w, y + h, ColorUtil.mulAlpha(p.textFaint(), 0.9f));
		return y + HEAD_H + 1;
	}

	@Override
	public void drawList(GuiGraphicsExtractor g, Palette p, HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows,
						 int x, int y, int w, int h) {
		int top = frame(g, p, block.icon(), false, block.title(), block.leftColumn(), block.rightColumn(), x, y, w, h);
		g.enableScissor(x, top, x + w, y + h - FOOT_H);
		float ry = top;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> drawRow(g, p, block.kind(), r.value(), x, rowY, w));
			ry += ROW_H * r.open();
		}
		g.disableScissor();
	}

	private static void drawRow(GuiGraphicsExtractor g, Palette p, HudData.Kind kind, HudData.Row row, int x, int rowY, int w) {
		row.lead().draw(g, p, x + 8, rowY + 3, 9);
		int nx = x + 22;
		SkinKit.text(g, row.name(), nx, rowY + 4, SkinKit.nameColor(p, kind, row));
		if (!row.sub().isEmpty()) {
			RenderUtil.text(g, row.sub(), RenderUtil.Face.SMALL, nx + SkinKit.w(row.name()) + 4, rowY + 5, p.textDim());
		}

		int right = x + w - 8;
		if (timed(kind)) {
			float cx = right - RING, cy = rowY + ROW_H / 2f;
			float progress = row.infinite() ? 1f : Math.max(0f, row.progress());
			SkinKit.ring(g, cx, cy, RING, 1.3f, progress, p.accent2(), ColorUtil.withAlpha(p.text(), 0x22));
			right -= RING * 2 + 4;
		}
		int cw = capsuleWidth(kind, row);
		int cx = right - cw;
		int cy = rowY + (ROW_H - CAP_H) / 2;
		SkinKit.capsule(g, cx, cy, cw, CAP_H, ColorUtil.withAlpha(p.text(), 0x10));
		RenderUtil.roundedOutline(g, cx, cy, cw, CAP_H, CAP_H / 2f, 0, ColorUtil.withAlpha(p.text(), 0x1A));
		String v = value(kind, row);
		SkinKit.text(g, v, cx + 7, cy + 2, kind == HudData.Kind.BINDS && !row.on() ? p.textDim() : p.text());
		if (kind == HudData.Kind.BINDS || kind == HudData.Kind.FRIENDS) {
			// On/off indicator: a ring, filled while the module is on (the friend is online).
			float dx = cx + cw - 7, dy = cy + CAP_H / 2f;
			int c = kind == HudData.Kind.FRIENDS ? SkinKit.statusColor(row) : p.text();
			RenderUtil.roundedOutline(g, dx - 2.5f, dy - 2.5f, 5, 5, 2.5f, 0.9f, ColorUtil.mulAlpha(c, 0.75f));
			if (row.on()) {
				RenderUtil.roundedRect(g, dx - 1.3f, dy - 1.3f, 2.6f, 2.6f, 1.3f, c);
			}
		}
	}

	// --- Watermark ----------------------------------------------------------------

	private static final int WM_H = 20;
	/** Space between the parts of the watermark row. */
	private static final int WM_GAP = 8;

	private static String wmValue(HudData.Part part) {
		return switch (part.id()) {
			case "ping" -> part.value() + " ms";
			case "bps" -> part.value() + " bps";
			default -> part.value();
		};
	}

	/** Icon, then the value in a capsule. */
	private static int wmPartWidth(HudData.Part part) {
		int textW = part.raw() ? RenderUtil.widthRaw(part.value()) : SkinKit.w(wmValue(part));
		return 9 + 4 + 6 + textW + 6;
	}

	private static int wmLeadWidth(HudData.Watermark wm) {
		return 19 + SkinKit.w(wm.client()) + 4 + SkinKit.w(wm.version());
	}

	/** Widest a watermark line may get before the next values wrap onto another line. */
	private static final int WM_MAX_W = 300;
	private static final int WM_LINE = 16;

	/** Splits the parts into lines: the first starts after the logo and title, all fit in {@link #WM_MAX_W}. */
	private static List<List<HudData.Part>> wmLines(HudData.Watermark wm) {
		List<List<HudData.Part>> lines = new ArrayList<>();
		List<HudData.Part> line = new ArrayList<>();
		int x = wmLeadWidth(wm);
		for (HudData.Part part : wm.parts()) {
			int pw = WM_GAP + wmPartWidth(part);
			if (!line.isEmpty() && x + pw > WM_MAX_W - 20) {
				lines.add(line);
				line = new ArrayList<>();
				x = 7 - WM_GAP;
			}
			line.add(part);
			x += pw;
		}
		lines.add(line);
		return lines;
	}

	@Override
	public Size measureWatermark(HudData.Watermark wm) {
		List<List<HudData.Part>> lines = wmLines(wm);
		int w = 0;
		for (int i = 0; i < lines.size(); i++) {
			int lw = i == 0 ? wmLeadWidth(wm) : 7 - WM_GAP;
			for (HudData.Part part : lines.get(i)) {
				lw += WM_GAP + wmPartWidth(part);
			}
			w = Math.max(w, lw);
		}
		return new Size(w + 20, WM_H + (lines.size() - 1) * WM_LINE);
	}

	/**
	 * Compact rows in the card look: logo, name and version, then the values in
	 * capsules side by side (wrapping onto another line only when they don't fit), "⋯".
	 */
	@Override
	public void drawWatermark(GuiGraphicsExtractor g, Palette p, HudData.Watermark wm, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		SkinKit.outline(g, p, x, y, w, h, R);
		g.enableScissor(x, y, x + w, y + h);
		RenderUtil.logo(g, x + 6, y + (WM_H - 8) / 2f, 8, p, false);
		int ty = y + (WM_H - 8) / 2;
		SkinKit.text(g, wm.client(), x + 19, ty, p.text());
		SkinKit.text(g, wm.version(), x + 19 + SkinKit.w(wm.client()) + 4, ty, p.textDim());
		List<List<HudData.Part>> lines = wmLines(wm);
		for (int i = 0; i < lines.size(); i++) {
			int lineY = y + i * WM_LINE;
			int px = i == 0 ? x + wmLeadWidth(wm) : x + 7 - WM_GAP;
			for (HudData.Part part : lines.get(i)) {
				px += WM_GAP;
				wmPart(g, p, part, px, lineY);
				px += wmPartWidth(part);
			}
		}
		g.disableScissor();
		SkinKit.menuDots(g, x + w - 10, y + WM_H / 2f, p.textFaint());
	}

	/** Icon (or skin head), then the value in a capsule, on the line starting at {@code lineY}. */
	private static void wmPart(GuiGraphicsExtractor g, Palette p, HudData.Part part, int px, int lineY) {
		if (part.lead() != HudData.Lead.NONE) {
			part.lead().draw(g, p, px, lineY + (WM_H - 9) / 2, 9);
		} else {
			part.icon().draw(g, px + 4.5f, lineY + WM_H / 2f, p.accent2());
		}
		int cx = px + 13;
		int cw = wmPartWidth(part) - 13;
		float cy = lineY + (WM_H - CAP_H) / 2f;
		SkinKit.capsule(g, cx, cy, cw, CAP_H, ColorUtil.withAlpha(p.text(), 0x10));
		RenderUtil.roundedOutline(g, cx, cy, cw, CAP_H, CAP_H / 2f, 0, ColorUtil.withAlpha(p.text(), 0x1A));
		int ty = lineY + (WM_H - 8) / 2;
		if (part.raw()) {
			RenderUtil.textRaw(g, part.value(), cx + 6, ty, p.text());
		} else {
			SkinKit.text(g, wmValue(part), cx + 6, ty, p.text());
		}
	}

	// --- Target -------------------------------------------------------------------

	@Override
	public Size measureTarget(HudData.Target t) {
		return new Size(156, HEAD_H + 1 + 30 + FOOT_H);
	}

	@Override
	public void drawTarget(GuiGraphicsExtractor g, Palette p, HudData.Target t, int x, int y, int w, int h) {
		int top = frame(g, p, HudIcon.TARGET, false, "Таргет", "Цель", "Здоровье", x, y, w, h);
		int head = 22;
		int tx = x + 8 + head + 7;
		int right = x + w - 8;
		RenderUtil.withAlpha(t.content(), () -> {
			t.drawHead(g, p, x + 8, top + 2, head, 4);
			// Health in a capsule with a ring of the remaining health.
			float rcx = right - RING, rcy = top + 8;
			SkinKit.ring(g, rcx, rcy, RING, 1.3f, t.health(), p.accent2(), ColorUtil.withAlpha(p.text(), 0x22));
			String hp = SkinKit.health(t.healthNumber(), false);
			int cw = 7 + SkinKit.w(hp) + 7;
			int cx = right - RING * 2 - 4 - cw;
			SkinKit.capsule(g, cx, top + 8 - CAP_H / 2f, cw, CAP_H, ColorUtil.withAlpha(p.text(), 0x10));
			RenderUtil.roundedOutline(g, cx, top + 8 - CAP_H / 2f, cw, CAP_H, CAP_H / 2f, 0, ColorUtil.withAlpha(p.text(), 0x1A));
			SkinKit.text(g, hp, cx + 7, top + 5, p.text());
			SkinKit.text(g, RenderUtil.ellipsize(t.name(), cx - 6 - tx, false), tx, top + 3, p.text());
			RenderUtil.text(g, t.isPlayer() ? "Игрок" : "Существо", RenderUtil.Face.SMALL, tx, top + 13, p.textDim());
		});
		SkinKit.healthBar(g, t, tx, top + 23, right - tx, 3, p.accent2(), p.accent(), ColorUtil.withAlpha(p.text(), 0x18));
	}
}
