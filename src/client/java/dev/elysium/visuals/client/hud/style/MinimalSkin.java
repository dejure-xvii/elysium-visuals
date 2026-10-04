package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * "Минимал": small separate dark translucent plates with rounded corners and
 * no block headers. Values on the right in the accent color.
 */
final class MinimalSkin implements Skin {
	private static final int PAD = 5;
	private static final int ROW_H = 11;
	private static final int LEAD = 8;
	private static final int R = 4;
	private static final int ALPHA = 0xB0;

	@Override
	public boolean keepsEmptyHeader() {
		return false;
	}

	private static String value(HudData.Kind kind, HudData.Row row) {
		return SkinKit.value(kind, row, false, "∞");
	}

	@Override
	public Size measureList(HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows) {
		int w = 64;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			HudData.Row row = r.value();
			int right = block.kind() == HudData.Kind.FRIENDS ? 4 : SkinKit.w(value(block.kind(), row));
			w = Math.max(w, PAD + LEAD + 4 + SkinKit.w(row.name()) + 10 + right + PAD);
		}
		return new Size(w, PAD - 1 + Math.round(Skin.rowsHeight(rows, ROW_H)) + PAD - 2);
	}

	@Override
	public void drawList(GuiGraphicsExtractor g, Palette p, HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows,
						 int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		g.enableScissor(x, y, x + w, y + h);
		float ry = y + PAD - 1;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			HudData.Row row = r.value();
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> {
				row.lead().draw(g, p, x + PAD, rowY + 1, LEAD);
				SkinKit.text(g, row.name(), x + PAD + LEAD + 4, rowY + 1, SkinKit.nameColor(p, block.kind(), row));
				if (block.kind() == HudData.Kind.FRIENDS) {
					RenderUtil.glowDot(g, x + w - PAD - 2, rowY + 5, 4, SkinKit.statusColor(row));
				} else {
					String v = value(block.kind(), row);
					int c = block.kind() == HudData.Kind.BINDS && !row.on() ? p.textDim() : p.accent2();
					SkinKit.text(g, v, x + w - PAD - SkinKit.w(v), rowY + 1, c);
				}
			});
			ry += ROW_H * r.open();
		}
		g.disableScissor();
	}

	@Override
	public Size measureWatermark(HudData.Watermark wm) {
		return new Size(PAD + 1 + SkinKit.wBold(wm.client()) + 4 + SkinKit.w(wm.version()) + PAD + 1, 15);
	}

	@Override
	public void drawWatermark(GuiGraphicsExtractor g, Palette p, HudData.Watermark wm, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		int tx = x + PAD + 1;
		SkinKit.bold(g, wm.client(), tx, y + 4, p.text());
		SkinKit.text(g, wm.version(), tx + SkinKit.wBold(wm.client()) + 4, y + 4, p.textDim());
	}

	@Override
	public Size measureTarget(HudData.Target t) {
		return new Size(112, 27);
	}

	@Override
	public void drawTarget(GuiGraphicsExtractor g, Palette p, HudData.Target t, int x, int y, int w, int h) {
		SkinKit.plate(g, p, x, y, w, h, R, ALPHA);
		int head = 12;
		RenderUtil.withAlpha(t.content(), () -> {
			t.drawHead(g, p, x + PAD, y + PAD, head, 2);
			String hp = SkinKit.health(t.healthNumber(), false);
			int hpX = x + w - PAD - SkinKit.w(hp);
			SkinKit.text(g, hp, hpX, y + PAD + 2, p.text());
			int nx = x + PAD + head + 5;
			SkinKit.text(g, RenderUtil.ellipsize(t.name(), hpX - 6 - nx, false), nx, y + PAD + 2, p.text());
		});
		SkinKit.healthBar(g, t, x + PAD, y + h - PAD - 2, w - PAD * 2, 2,
				ColorUtil.withAlpha(p.text(), 0xD0), ColorUtil.withAlpha(p.text(), 0xF0), ColorUtil.withAlpha(p.text(), 0x26));
	}
}
