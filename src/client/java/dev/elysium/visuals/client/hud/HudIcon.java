package dev.elysium.visuals.client.hud;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small vector icons for HUD headers and the watermark, built from the same
 * anti-aliased shapes as the GUI. Each icon fits an 8×8 box centered on (cx, cy).
 */
public enum HudIcon {
	/** Lightning bolt: frames per second. */
	FPS {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.stroke(g, cx + 1.4f, cy - 3.6f, cx - 1.8f, cy + 0.4f, 1.1f, color);
			RenderUtil.stroke(g, cx - 1.8f, cy + 0.4f, cx + 1.8f, cy - 0.4f, 1.1f, color);
			RenderUtil.stroke(g, cx + 1.8f, cy - 0.4f, cx - 1.4f, cy + 3.6f, 1.1f, color);
		}
	},
	/** Three rising signal bars: ping. */
	PING {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			float w = 1.8f, bottom = cy + 3.8f;
			RenderUtil.roundedRect(g, cx - 3.6f, bottom - 3f, w, 3f, 0.7f, color);
			RenderUtil.roundedRect(g, cx - 0.9f, bottom - 5.2f, w, 5.2f, 0.7f, color);
			RenderUtil.roundedRect(g, cx + 1.8f, bottom - 7.6f, w, 7.6f, 0.7f, color);
		}
	},
	/** Clock face with two hands. */
	TIME {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 4, cy - 4, 8, 8, 4, 1.1f, color);
			RenderUtil.stroke(g, cx, cy + 0.2f, cx, cy - 2.2f, 1f, color);
			RenderUtil.stroke(g, cx, cy + 0.2f, cx + 1.7f, cy + 1f, 1f, color);
		}
	},
	/** Two stacked server units. */
	SERVER {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 4, cy - 4, 8, 3.6f, 1.1f, 1f, color);
			RenderUtil.roundedOutline(g, cx - 4, cy + 0.4f, 8, 3.6f, 1.1f, 1f, color);
			RenderUtil.roundedRect(g, cx - 2.6f, cy - 2.75f, 1.1f, 1.1f, 0.55f, color);
			RenderUtil.roundedRect(g, cx - 2.6f, cy + 1.65f, 1.1f, 1.1f, 0.55f, color);
		}
	},
	/** Map pin: coordinates. */
	COORDS {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 3, cy - 4.2f, 6, 6, 3, 1.1f, color);
			RenderUtil.roundedRect(g, cx - 0.8f, cy - 2f, 1.6f, 1.6f, 0.8f, color);
			RenderUtil.stroke(g, cx - 1.6f, cy + 1.2f, cx, cy + 3.8f, 1.1f, color);
			RenderUtil.stroke(g, cx + 1.6f, cy + 1.2f, cx, cy + 3.8f, 1.1f, color);
		}
	},
	/** Head and shoulders. */
	PLAYER {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedRect(g, cx - 1.8f, cy - 4, 3.6f, 3.6f, 1.8f, color);
			RenderUtil.roundedRect(g, cx - 3.6f, cy + 0.4f, 7.2f, 3.6f, 1.8f, color);
		}
	},
	/** Two people: friends. */
	FRIENDS {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			int back = ColorUtil.mulAlpha(color, 0.55f);
			RenderUtil.roundedRect(g, cx + 0.6f, cy - 3.6f, 3f, 3f, 1.5f, back);
			RenderUtil.roundedRect(g, cx + 0.2f, cy + 0.4f, 4.2f, 3.4f, 1.6f, back);
			RenderUtil.roundedRect(g, cx - 3.2f, cy - 4, 3.4f, 3.4f, 1.7f, color);
			RenderUtil.roundedRect(g, cx - 4.4f, cy + 0.2f, 5.8f, 3.8f, 1.8f, color);
		}
	},
	/** Keyboard: key binds. */
	KEYBOARD {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 4.5f, cy - 3.2f, 9, 6.4f, 1.6f, 1f, color);
			for (int i = 0; i < 3; i++) {
				RenderUtil.roundedRect(g, cx - 2.75f + i * 2.1f, cy - 1.5f, 1.2f, 1.2f, 0.4f, color);
			}
			RenderUtil.roundedRect(g, cx - 2f, cy + 0.7f, 4f, 1.1f, 0.5f, color);
		}
	},
	/** Potion flask: effects. */
	POTION {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 1.4f, cy - 4.2f, 2.8f, 3.2f, 0.6f, 1f, color);
			RenderUtil.roundedOutline(g, cx - 3.6f, cy - 1.6f, 7.2f, 5.8f, 2.9f, 1.1f, color);
			RenderUtil.roundedRect(g, cx - 2.2f, cy + 1.2f, 4.4f, 1.6f, 0.8f, ColorUtil.mulAlpha(color, 0.75f));
		}
	},
	/** Stopwatch: cooldowns. */
	TIMER {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 3.7f, cy - 2.9f, 7.4f, 7.4f, 3.7f, 1.1f, color);
			RenderUtil.roundedRect(g, cx - 1.1f, cy - 4.6f, 2.2f, 1.1f, 0.5f, color);
			RenderUtil.stroke(g, cx, cy + 0.8f, cx + 1.4f, cy - 0.7f, 1f, color);
		}
	},
	/** Ring with a dot: target. */
	TARGET {
		@Override
		public void draw(GuiGraphicsExtractor g, float cx, float cy, int color) {
			RenderUtil.roundedOutline(g, cx - 4, cy - 4, 8, 8, 4, 1.1f, color);
			RenderUtil.roundedRect(g, cx - 1.2f, cy - 1.2f, 2.4f, 2.4f, 1.2f, color);
		}
	};

	public abstract void draw(GuiGraphicsExtractor g, float cx, float cy, int color);
}
