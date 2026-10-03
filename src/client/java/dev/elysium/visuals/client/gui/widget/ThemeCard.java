package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.Theme;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A clickable glass card with a miniature of the ClickGUI in that theme. */
public class ThemeCard extends UiElement {
	private final Theme theme;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue selected;

	public ThemeCard(Theme theme) {
		this.theme = theme;
		this.selected = new SmoothValue(isSelected() ? 1 : 0, 18f);
	}

	private boolean isSelected() {
		return ThemeManager.get().active() == theme;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) ? 1f : 0f);
		float s = selected.update(isSelected() ? 1f : 0f);

		RenderUtil.glass(g, x, y, width, height, 8, p, h, s * 0.6f);

		// Miniature window in the theme's own palette.
		Palette tp = Palette.of(theme);
		float px = x + 5, py = y + 5, pw = width - 10, ph = height - 21;
		if (theme.glass()) {
			// Glass needs something colorful behind it to read as glass.
			RenderUtil.roundedGradient(g, px, py, pw, ph, 5, 0xFFFF7AB6, 0xFF5B8CFF);
		}
		int bgA = Math.max(ColorUtil.alpha(tp.background()), theme.glass() ? 0x50 : 0xFF);
		RenderUtil.roundedGradient(g, px, py, pw, ph, 5, ColorUtil.withAlpha(tp.bgTop(), bgA), ColorUtil.withAlpha(tp.bgBottom(), bgA));
		if (tp.isDark() && !theme.glass()) {
			g.enableScissor((int) px, (int) py, (int) (px + pw), (int) (py + ph));
			RenderUtil.blob(g, px + 4, py + 2, ph * 0.7f, tp.blob1(), 0.4f);
			RenderUtil.blob(g, px + pw - 2, py + ph, ph * 0.6f, tp.blob3(), 0.3f);
			g.disableScissor();
		}
		RenderUtil.roundedOutline(g, px, py, pw, ph, 5, 0, tp.border());

		// Icon sidebar with the logo and the selected item.
		float sbw = 9;
		RenderUtil.roundedRect(g, px + 2, py + 2, sbw, ph - 4, 3, tp.sidebar());
		RenderUtil.roundedRect(g, px + 3.5f, py + 3.5f, 6, 6, 1.6f, tp.accent());
		RenderUtil.roundedRect(g, px + 3.5f, py + 12, 6, 6, 1.6f, tp.selectedTab());
		RenderUtil.roundedRect(g, px + 5, py + 20.5f, 3, 3, 1, tp.textFaint());

		// Title, caption and a module card with a switch.
		float cx = px + sbw + 6, cw = pw - (cx - px) - 4;
		RenderUtil.roundedRect(g, cx, py + 4, cw * 0.45f, 3, 1.5f, tp.text());
		RenderUtil.roundedRect(g, cx, py + 9, cw * 0.3f, 2, 1, tp.textFaint());
		float cardY = py + 14, cardH = ph - 18;
		if (cardH > 4) {
			RenderUtil.roundedRect(g, cx, cardY, cw, cardH, 2.5f, tp.surface());
			RenderUtil.roundedOutline(g, cx, cardY, cw, cardH, 2.5f, 0, tp.border());
			RenderUtil.roundedRect(g, cx + 3, cardY + cardH / 2f - 1, cw * 0.35f, 2, 1, tp.textDim());
			RenderUtil.roundedRect(g, cx + cw - 10, cardY + cardH / 2f - 2, 7, 4, 2, tp.accent());
		}

		// Name, with an accent dot when selected.
		int nameColor = ColorUtil.mix(p.textDim(), p.text(), Math.max(h, s));
		String name = RenderUtil.ellipsize(theme.name(), width - 14, s > 0.5f);
		int nameW = s > 0.5f ? RenderUtil.widthBold(name) : RenderUtil.width(name);
		int dot = s > 0.01f ? 6 : 0;
		int nameX = x + (width - nameW - dot) / 2 + dot;
		if (s > 0.01f) {
			RenderUtil.withAlpha(s, () -> RenderUtil.glowDot(g, nameX - 4.5f, y + height - 8.5f, 3.5f, p.accent2()));
		}
		RenderUtil.text(g, name, s > 0.5f ? RenderUtil.Face.BOLD : RenderUtil.Face.REGULAR, nameX, y + height - 12, nameColor);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		ThemeManager.get().select(theme);
		return true;
	}
}
