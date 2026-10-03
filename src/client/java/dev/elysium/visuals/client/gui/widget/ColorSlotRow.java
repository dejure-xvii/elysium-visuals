package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.ColorSlot;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One editable color of the custom theme: swatch, name and HEX value. */
public class ColorSlotRow extends UiElement {
	private final ColorSlot slot;
	private final BooleanSupplier selected;
	private final Consumer<ColorSlot> onSelect;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue sel;

	public ColorSlotRow(ColorSlot slot, BooleanSupplier selected, Consumer<ColorSlot> onSelect) {
		this.slot = slot;
		this.selected = selected;
		this.onSelect = onSelect;
		this.sel = new SmoothValue(selected.getAsBoolean() ? 1 : 0, 14f);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) ? 1f : 0f);
		float s = sel.update(selected.getAsBoolean() ? 1f : 0f);
		RenderUtil.well(g, x, y, width, height, 5, p, h);
		if (s > 0.01f) {
			RenderUtil.roundedRect(g, x, y, width, height, 5, ColorUtil.mulAlpha(p.accentSoft(), s));
			RenderUtil.roundedOutline(g, x, y, width, height, 5, 0, ColorUtil.mulAlpha(p.accent2(), 0.45f * s));
		}

		int color = ThemeManager.get().custom().color(slot);
		int sw = height - 8;
		int sx = x + 8, sy = y + 4;
		if (ColorUtil.alpha(color) < 0xFF) {
			RenderUtil.checkerboard(g, sx + 1, sy + 1, sw - 2, sw - 2, 3);
		}
		RenderUtil.roundedRect(g, sx, sy, sw, sw, sw / 2f, color);
		RenderUtil.roundedOutline(g, sx, sy, sw, sw, sw / 2f, 0, p.highlight());

		int textX = sx + sw + 6;
		int textY = y + (height - 8) / 2;

		// The HEX value is always shown; the name gets shortened if space runs out.
		String hex = ColorUtil.toHex(color);
		int hexX = x + width - 7 - RenderUtil.width(hex);
		RenderUtil.text(g, hex, RenderUtil.Face.REGULAR, hexX, textY, ColorUtil.mix(p.textFaint(), p.accent2(), s));
		String name = RenderUtil.ellipsize(slot.displayName(), hexX - 6 - textX, false);
		RenderUtil.text(g, name, RenderUtil.Face.REGULAR, textX, textY, p.text());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		onSelect.accept(slot);
		return true;
	}
}
