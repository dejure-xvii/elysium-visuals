package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Плашки": everything is made of separate dark plates. The watermark is a set
 * of chips with accent icons (speed and coordinates on a second line); a block
 * has its header in its own plate and every row in its own plate under it.
 */
final class PlatesSkin implements Skin {
	private static final int R = 4;
	private static final int ALPHA = 0xD8;
	private static final int GAP = 2;
	private static final int HEAD_H = 16;
	private static final int ROW_H = 14;
	private static final int CHIP_H = 15;
	private static final int CHIP_GAP = 3;
	/** Watermark parts shown on the second line. */
	private static final Set<String> SECOND_LINE = Set.of("bps", "coords");

	@Override
	public boolean keepsEmptyHeader() {
		return false;
	}

	// --- Lists --------------------------------------------------------------------

	private static String value(HudData.Kind kind, HudData.Row row) {
		return SkinKit.value(kind, row, true, "∞");
	}

	/** Width of the right-hand plate (time) or key square of a row; 0 if the row has none. */
	private static int sideWidth(HudData.Kind kind, HudData.Row row) {
		return switch (kind) {
			case BINDS -> Math.max(ROW_H, SkinKit.w(row.value()) + 8);
			case BUFFS, COOLDOWNS -> SkinKit.w(value(kind, row)) + 10;
			default -> 0;
		};
	}

	/** Width of the main plate's content. */
	private static int mainWidth(HudData.Kind kind, HudData.Row row) {
		int w = 5 + 8 + 4 + SkinKit.w(row.name()) + 6;
		if (kind == HudData.Kind.BUFFS) {
			w += 8;
		}
		if (kind == HudData.Kind.FRIENDS) {
			w += 10;
		}
		return w;
	}

	@Override
	public Size measureList(HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows) {
		int w = 6 + SkinKit.w(block.title()) + 10 + 10;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			int side = sideWidth(block.kind(), r.value());
			w = Math.max(w, mainWidth(block.kind(), r.value()) + (side > 0 ? GAP + side : 0));
		}
		return new Size(w, HEAD_H + Math.round(Skin.rowsHeight(rows, ROW_H + GAP)));
	}

	@Override
	public void drawList(GuiGraphicsExtractor g, Palette p, HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows,
						 int x, int y, int w, int h) {
		HudData.Kind kind = block.kind();
		SkinKit.plate(g, p, x, y, w, HEAD_H, R, ALPHA);
		SkinKit.text(g, block.title(), x + 6, y + 4, p.text());
		block.icon().draw(g, x + w - 9, y + HEAD_H / 2f, p.accent2());

		float ry = y + HEAD_H + GAP;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			HudData.Row row = r.value();
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> {
				int side = sideWidth(kind, row);
				// Binds: the name plate hugs its text and the key square sits right next to it.
				int mainW = kind == HudData.Kind.BINDS ? mainWidth(kind, row)
						: side > 0 ? w - GAP - side : w;
				SkinKit.plate(g, p, x, rowY, mainW, ROW_H, R, ALPHA);
				int tx = x + 5;
				row.lead().draw(g, p, tx, rowY + 3, 8);
				tx += 12;
				if (kind == HudData.Kind.BUFFS) {
					RenderUtil.stroke(g, tx, rowY + 4.5f, tx + 2.5f, rowY + 7f, 1f, p.textDim());
					RenderUtil.stroke(g, tx + 2.5f, rowY + 7f, tx, rowY + 9.5f, 1f, p.textDim());
					tx += 8;
				}
				SkinKit.text(g, row.name(), tx, rowY + 3, SkinKit.nameColor(p, kind, row));
				if (kind == HudData.Kind.FRIENDS) {
					RenderUtil.glowDot(g, x + mainW - 7, rowY + ROW_H / 2f, 4, SkinKit.statusColor(row));
				}
				if (side > 0) {
					int sx = kind == HudData.Kind.BINDS ? x + mainW + GAP : x + w - side;
					SkinKit.plate(g, p, sx, rowY, side, ROW_H, R, ALPHA);
					String v = kind == HudData.Kind.BINDS ? row.value() : value(kind, row);
					int c = kind == HudData.Kind.BINDS && !row.on() ? p.textDim() : p.text();
					SkinKit.text(g, v, sx + (side - SkinKit.w(v)) / 2, rowY + 3, c);
				}
			});
			ry += (ROW_H + GAP) * r.open();
		}
	}

	// --- Watermark ----------------------------------------------------------------

	private static String chipText(HudData.Part part) {
		return switch (part.id()) {
			case "fps" -> "FPS: " + part.value();
			case "ping" -> part.value() + " ms";
			case "bps" -> part.value() + "BPS";
			default -> part.value();
		};
	}

	private static int chipWidth(HudData.Part part) {
		int textW = part.raw() ? RenderUtil.widthRaw(part.value()) : SkinKit.w(chipText(part));
		return 5 + 8 + 4 + textW + 6;
	}

	private static int brandWidth(HudData.Watermark wm) {
		return 5 + 9 + 4 + SkinKit.wBold(wm.client()) + 6;
	}

	private static List<List<HudData.Part>> lines(HudData.Watermark wm) {
		List<HudData.Part> first = new ArrayList<>(), second = new ArrayList<>();
		for (HudData.Part part : wm.parts()) {
			(SECOND_LINE.contains(part.id()) ? second : first).add(part);
		}
		return List.of(first, second);
	}

	@Override
	public Size measureWatermark(HudData.Watermark wm) {
		List<List<HudData.Part>> lines = lines(wm);
		int w1 = brandWidth(wm);
		for (HudData.Part part : lines.get(0)) {
			w1 += CHIP_GAP + chipWidth(part);
		}
		int w2 = -CHIP_GAP;
		for (HudData.Part part : lines.get(1)) {
			w2 += CHIP_GAP + chipWidth(part);
		}
		boolean two = !lines.get(1).isEmpty();
		return new Size(Math.max(w1, w2), two ? CHIP_H * 2 + 4 : CHIP_H);
	}

	@Override
	public void drawWatermark(GuiGraphicsExtractor g, Palette p, HudData.Watermark wm, int x, int y, int w, int h) {
		List<List<HudData.Part>> lines = lines(wm);
		g.enableScissor(x - 4, y - 4, x + w + 4, y + h + 4);
		int cx = x;
		int bw = brandWidth(wm);
		SkinKit.plate(g, p, cx, y, bw, CHIP_H, R, ALPHA);
		RenderUtil.logo(g, cx + 5, y + 3, 9, p, false);
		SkinKit.bold(g, wm.client(), cx + 18, y + 4, p.text());
		cx += bw + CHIP_GAP;
		for (HudData.Part part : lines.get(0)) {
			cx += chip(g, p, part, cx, y) + CHIP_GAP;
		}
		cx = x;
		for (HudData.Part part : lines.get(1)) {
			cx += chip(g, p, part, cx, y + CHIP_H + 4) + CHIP_GAP;
		}
		g.disableScissor();
	}

	private static int chip(GuiGraphicsExtractor g, Palette p, HudData.Part part, int x, int y) {
		int cw = chipWidth(part);
		SkinKit.plate(g, p, x, y, cw, CHIP_H, R, ALPHA);
		part.icon().draw(g, x + 9, y + CHIP_H / 2f, p.accent2());
		if (part.raw()) {
			RenderUtil.textRaw(g, part.value(), x + 17, y + 4, p.text());
		} else {
			SkinKit.text(g, chipText(part), x + 17, y + 4, p.text());
		}
		return cw;
	}

	// --- Target -------------------------------------------------------------------

	@Override
	public Size measureTarget(HudData.Target t) {
		return new Size(150, 46);
	}

	@Override
	public void drawTarget(GuiGraphicsExtractor g, Palette p, HudData.Target t, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, 6, 0xE4);
		int head = 24;
		RenderUtil.withAlpha(t.content(), () -> {
			t.drawHead(g, p, x + 6, y + 6, head, 4);
			String hp = SkinKit.health(t.healthNumber(), false) + "hp";
			int hpX = x + w - 7 - SkinKit.w(hp);
			SkinKit.text(g, hp, hpX, y + 9, p.text());
			int nx = x + 6 + head + 6;
			SkinKit.text(g, RenderUtil.ellipsize(t.name(), hpX - 6 - nx, false), nx, y + 9, p.text());
		});
		SkinKit.healthBar(g, t, x + 6, y + h - 12, w - 12, 6, p.accent2(), p.accent(), ColorUtil.withAlpha(p.text(), 0x18));
	}
}
