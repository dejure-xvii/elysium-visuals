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
	/** Key prefix of watermark rows whose value NameProtect must leave alone. */
	private static final String RAW = "raw:";

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
		int w = 7 + (row.key().startsWith(RAW) ? RenderUtil.widthRaw(v) : SkinKit.w(v)) + 7;
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
		int vc = kind == HudData.Kind.BINDS && !row.on() ? p.textDim() : p.text();
		if (row.key().startsWith(RAW)) {
			RenderUtil.textRaw(g, v, cx + 7, cy + 2, vc);
		} else {
			SkinKit.text(g, v, cx + 7, cy + 2, vc);
		}
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

	private static List<HudData.Row> infoRows(HudData.Watermark wm) {
		List<HudData.Row> list = new ArrayList<>();
		for (HudData.Part part : wm.parts()) {
			HudData.Lead lead = part.lead() != HudData.Lead.NONE ? part.lead() : HudData.Lead.icon(part.icon());
			list.add(HudData.Row.text((part.raw() ? RAW : "") + part.id(), lead, part.label(), "", part.raw() ? part.value() : unit(part), true));
		}
		return list;
	}

	private static String unit(HudData.Part part) {
		return switch (part.id()) {
			case "ping" -> part.value() + " ms";
			default -> part.value();
		};
	}

	@Override
	public Size measureWatermark(HudData.Watermark wm) {
		String title = wm.client() + " " + wm.version();
		int w = frameWidth(title, "Параметр", "Значение");
		List<HudData.Row> rows = infoRows(wm);
		for (HudData.Row row : rows) {
			w = Math.max(w, rowWidth(HudData.Kind.INFO, row));
		}
		return new Size(w, HEAD_H + 1 + rows.size() * ROW_H + FOOT_H);
	}

	@Override
	public void drawWatermark(GuiGraphicsExtractor g, Palette p, HudData.Watermark wm, int x, int y, int w, int h) {
		int top = frame(g, p, null, true, wm.client(), "Параметр", "Значение", x, y, w, h);
		SkinKit.text(g, wm.version(), x + 19 + SkinKit.w(wm.client()) + 4, y + 5, p.textDim());
		g.enableScissor(x, top, x + w, y + h - FOOT_H);
		int rowY = top;
		for (HudData.Row row : infoRows(wm)) {
			drawRow(g, p, HudData.Kind.INFO, row, x, rowY, w);
			rowY += ROW_H;
		}
		g.disableScissor();
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
			SkinKit.text(g, RenderUtil.ellipsize(t.name(), cx - 6 - tx, false), tx, top + 4, p.text());
			RenderUtil.text(g, "Игрок", RenderUtil.Face.SMALL, tx, top + 14, p.textDim());
		});
		SkinKit.healthBar(g, t, tx, top + 22, right - tx, 3, p.accent2(), p.accent(), ColorUtil.withAlpha(p.text(), 0x18));
	}
}
