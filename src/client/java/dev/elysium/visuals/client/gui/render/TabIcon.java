package dev.elysium.visuals.client.gui.render;

import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Vector icons for sidebar tabs, built from the same anti-aliased shapes as
 * the rest of the GUI so they stay sharp and consistent at any scale.
 * Each icon fits a 10×10 box centered on (cx, cy).
 */
public enum TabIcon {
	/** Palette: ring with three color dots. */
	THEMES {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 5, cy - 5, 10, 10, 5, 1.2f, color);
			RenderUtil.roundedRect(g, cx - 2.75f, cy - 2.25f, 2.2f, 2.2f, 1.1f, color);
			RenderUtil.roundedRect(g, cx + 0.55f, cy - 2.75f, 2.2f, 2.2f, 1.1f, color);
			RenderUtil.roundedRect(g, cx - 1.1f, cy + 0.6f, 2.2f, 2.2f, 1.1f, ColorUtil.mulAlpha(color, 0.6f));
		}
	},
	/** Person: head and shoulders. */
	PLAYER {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedRect(g, cx - 2.25f, cy - 5, 4.5f, 4.5f, 2.25f, color);
			RenderUtil.roundedRect(g, cx - 4.5f, cy + 0.5f, 9, 4.5f, 2.25f, color);
		}
	},
	/** Eye: outlined lens with a pupil. */
	RENDER {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 5, cy - 3.25f, 10, 6.5f, 3.25f, 1.2f, color);
			RenderUtil.roundedRect(g, cx - 1.6f, cy - 1.6f, 3.2f, 3.2f, 1.6f, color);
		}
	},
	/** Grid of four rounded squares. */
	UTILS {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			float s = 4.2f, gap = 1.6f;
			RenderUtil.roundedRect(g, cx - s - gap / 2, cy - s - gap / 2, s, s, 1.2f, color);
			RenderUtil.roundedRect(g, cx + gap / 2, cy - s - gap / 2, s, s, 1.2f, ColorUtil.mulAlpha(color, 0.6f));
			RenderUtil.roundedRect(g, cx - s - gap / 2, cy + gap / 2, s, s, 1.2f, ColorUtil.mulAlpha(color, 0.6f));
			RenderUtil.roundedRect(g, cx + gap / 2, cy + gap / 2, s, s, 1.2f, color);
		}
	},
	/** Sprout: stem with two leaves. */
	FARM {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedRect(g, cx - 0.6f, cy - 1.5f, 1.2f, 6.5f, 0.6f, color);
			leaf(g, cx - 2.6f, cy - 2.2f, -0.7f, color);
			leaf(g, cx + 2.6f, cy - 3.6f, 0.7f, color);
		}

		private void leaf(GuiGraphicsExtractor g, float x, float y, float angle, int color) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().rotate(angle);
			RenderUtil.roundedRect(g, -1.6f, -3, 3.2f, 6, 1.6f, color);
			g.pose().popMatrix();
		}
	},

	/** Screen with a widget in the corner: the HUD editor. */
	HUD {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 5, cy - 4, 10, 8, 2, 1.2f, color);
			RenderUtil.roundedRect(g, cx - 3.2f, cy - 2.2f, 3.6f, 2.4f, 0.8f, color);
			RenderUtil.roundedRect(g, cx + 0.4f, cy + 0.6f, 2.8f, 1.6f, 0.8f, ColorUtil.mulAlpha(color, 0.6f));
		}
	};

	public abstract void draw(GuiGraphicsExtractor g, float cx, float cy, int color);
}
