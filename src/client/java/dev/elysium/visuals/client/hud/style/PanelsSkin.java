package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * "Панели": dark panels with a bold accent title in the top-left corner and
 * plain rows inside; an empty block shrinks to its title. The watermark is one
 * line on a dark plate with dot separators.
 */
final class PanelsSkin implements Skin {
	private static final int R = 5;
	private static final int ALPHA = 0xC8;
	private static final int PAD = 7;
	private static final int TITLE_H = 17;
	private static final int ROW_H = 12;
	private static final int KEY = 10;
	/** Space taken by a "•" separator in the watermark. */
	private static final int SEP = 11;

	@Override
	public boolean keepsEmptyHeader() {
		return true;
	}

	private static String value(HudData.Kind kind, HudData.Row row) {
		return SkinKit.value(kind, row, false, "**:**");
	}

	@Override
	public Size measureList(HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows) {
		int w = Math.max(92, PAD + SkinKit.wBold(block.title()) + PAD + 20);
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			HudData.Row row = r.value();
			int right = switch (block.kind()) {
				case BINDS -> Math.max(KEY, SkinKit.w(row.value()) + 5);
				case FRIENDS -> 4;
				default -> SkinKit.w(value(block.kind(), row));
			};
			w = Math.max(w, PAD + 9 + 5 + SkinKit.w(row.name()) + 14 + right + PAD);
		}
		float body = Skin.rowsHeight(rows, ROW_H);
		// Empty: just the title; the bottom padding opens together with the first row.
		float open = Math.min(1f, body / ROW_H);
		return new Size(w, TITLE_H + Math.round(body + 3 * open));
	}

	@Override
	public void drawList(GuiGraphicsExtractor g, Palette p, HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows,
						 int x, int y, int w, int h) {
		HudData.Kind kind = block.kind();
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		SkinKit.bold(g, block.title(), x + PAD, y + 5, p.accent2());
		g.enableScissor(x, y, x + w, y + h);
		float ry = y + TITLE_H;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			HudData.Row row = r.value();
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> {
				row.lead().draw(g, p, x + PAD, rowY + 1, 9);
				int nx = x + PAD + 14;
				SkinKit.text(g, row.name(), nx, rowY + 2, SkinKit.nameColor(p, kind, row));
				switch (kind) {
					case BINDS -> {
						int kw = Math.max(KEY, SkinKit.w(row.value()) + 5);
						int kx = x + w - PAD - kw;
						RenderUtil.roundedRect(g, kx, rowY + 1, kw, KEY, 2.5f, SkinKit.dark(p, 0xF0));
						RenderUtil.roundedOutline(g, kx, rowY + 1, kw, KEY, 2.5f, 0, ColorUtil.withAlpha(p.text(), 0x14));
						SkinKit.text(g, row.value(), kx + (kw - SkinKit.w(row.value()) + 1) / 2, rowY + 2,
								row.on() ? p.text() : p.textDim());
					}
					case FRIENDS -> RenderUtil.glowDot(g, x + w - PAD - 2, rowY + 6, 4, SkinKit.statusColor(row));
					default -> {
						String v = value(kind, row);
						SkinKit.text(g, v, x + w - PAD - SkinKit.w(v), rowY + 2, ColorUtil.mulAlpha(p.text(), 0.85f));
					}
				}
			});
			ry += ROW_H * r.open();
		}
		g.disableScissor();
	}

	// --- Watermark ----------------------------------------------------------------

	private static String partText(HudData.Part part) {
		return switch (part.id()) {
			case "fps" -> part.value() + "fps";
			case "ping" -> part.value() + "ms";
			case "bps" -> part.value() + " bps";
			default -> part.value();
		};
	}

	private static int partWidth(HudData.Part part) {
		return 11 + (part.raw() ? RenderUtil.widthRaw(part.value()) : SkinKit.w(partText(part)));
	}

	@Override
	public Size measureWatermark(HudData.Watermark wm) {
		int w = PAD + SkinKit.wBold(wm.client());
		for (HudData.Part part : wm.parts()) {
			w += SEP + partWidth(part);
		}
		return new Size(w + PAD, 16);
	}

	@Override
	public void drawWatermark(GuiGraphicsExtractor g, Palette p, HudData.Watermark wm, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		g.enableScissor(x, y, x + w, y + h);
		int tx = x + PAD;
		int ty = y + 4;
		SkinKit.bold(g, wm.client(), tx, ty, p.accent2());
		tx += SkinKit.wBold(wm.client());
		for (HudData.Part part : wm.parts()) {
			RenderUtil.roundedRect(g, tx + SEP / 2f - 1, y + h / 2f - 1, 2, 2, 1, p.textFaint());
			tx += SEP;
			if (part.lead() != HudData.Lead.NONE) {
				part.lead().draw(g, p, tx, y + 4, 8);
			} else {
				part.icon().draw(g, tx + 4, y + h / 2f, ColorUtil.mulAlpha(p.text(), 0.8f));
			}
			tx += 11;
			if (part.raw()) {
				RenderUtil.textRaw(g, part.value(), tx, ty, p.text());
				tx += RenderUtil.widthRaw(part.value());
			} else {
				SkinKit.text(g, partText(part), tx, ty, p.text());
				tx += SkinKit.w(partText(part));
			}
		}
		g.disableScissor();
	}

	// --- Target -------------------------------------------------------------------

	@Override
	public Size measureTarget(HudData.Target t) {
		return new Size(150, 40);
	}

	@Override
	public void drawTarget(GuiGraphicsExtractor g, Palette p, HudData.Target t, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		int head = 30;
		int tx = x + 5 + head + 6;
		RenderUtil.withAlpha(t.content(), () -> {
			t.drawHead(g, p, x + 5, y + 5, head, 3);
			SkinKit.bold(g, RenderUtil.ellipsize(t.name(), x + w - 6 - tx, true), tx, y + 6, p.text());
			SkinKit.bold(g, "HP:", tx, y + 16, p.accent2());
			SkinKit.text(g, SkinKit.health(t.healthNumber(), true), tx + SkinKit.wBold("HP:") + 3, y + 16, p.text());
		});
		SkinKit.healthBar(g, t, tx, y + h - 13, x + w - 6 - tx, 7, SkinKit.WARM, p.accent(),
				ColorUtil.withAlpha(p.text(), 0x1C));
	}
}
