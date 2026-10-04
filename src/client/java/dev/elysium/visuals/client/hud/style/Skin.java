package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * One Interface style: measures and draws the watermark, the target and the
 * list blocks (binds, buffs, cooldowns, friends). Rows come with their
 * appear/disappear animation ({@link AnimatedRows.Row#shown()} / {@link AnimatedRows.Row#open()}).
 */
public interface Skin {
	record Size(int width, int height) {
	}

	/**
	 * True: a block without rows stays visible as its header; false: it is hidden.
	 */
	boolean keepsEmptyHeader();

	Size measureList(HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows);

	void drawList(GuiGraphicsExtractor g, Palette p, HudData.Block block, List<AnimatedRows.Row<HudData.Row>> rows,
				  int x, int y, int w, int h);

	Size measureWatermark(HudData.Watermark wm);

	void drawWatermark(GuiGraphicsExtractor g, Palette p, HudData.Watermark wm, int x, int y, int w, int h);

	Size measureTarget(HudData.Target t);

	void drawTarget(GuiGraphicsExtractor g, Palette p, HudData.Target t, int x, int y, int w, int h);

	/** Sum of the animated row heights. */
	static float rowsHeight(List<AnimatedRows.Row<HudData.Row>> rows, float rowH) {
		float h = 0;
		for (AnimatedRows.Row<HudData.Row> r : rows) {
			h += rowH * r.open();
		}
		return h;
	}
}
