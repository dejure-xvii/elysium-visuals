package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A color setting: a row with a swatch that expands into a picker and HEX field. */
public class ColorSettingWidget extends UiContainer {
	private static final int ROW_H = 18;
	private static final int SQUARE = 56;
	private static final int FIELD_H = 16;

	private final ColorSetting setting;
	private final ColorPicker picker;
	private final HexField hex;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue expand = new SmoothValue(0, 16f);
	private boolean expanded;

	public ColorSettingWidget(ColorSetting setting) {
		this.setting = setting;
		this.picker = add(new ColorPicker(setting::argb, setting::set));
		this.hex = add(new HexField(setting::argb, setting::set));
		this.height = ROW_H;
	}

	/** Field goes next to the picker if there is room, otherwise below it. */
	private boolean fieldBelow() {
		return width < 7 + ColorPicker.widthFor(SQUARE) + 8 + 70 + 7;
	}

	private int expandedExtra() {
		return 6 + SQUARE + 6 + (fieldBelow() ? FIELD_H + 6 : 0);
	}

	@Override
	public int preferredHeight() {
		return ROW_H + Math.round(expand.get() * expandedExtra());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float e = expand.update(expanded ? 1f : 0f);
		boolean rowHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_H;
		float h = hover.update(rowHovered ? 1f : 0f);

		RenderUtil.well(g, x, y, width, height, 5, p, Math.max(h, e * 0.5f));

		int c = setting.argb();
		int sw = ROW_H - 8;
		int sx = x + 8, sy = y + 4;
		if (ColorUtil.alpha(c) < 0xFF) {
			RenderUtil.checkerboard(g, sx + 1, sy + 1, sw - 2, sw - 2, 3);
		}
		RenderUtil.softGlow(g, sx, sy, sw, sw, sw / 2f, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 0xFF), 0.35f), 3);
		RenderUtil.roundedRect(g, sx, sy, sw, sw, sw / 2f, c);
		RenderUtil.roundedOutline(g, sx, sy, sw, sw, sw / 2f, 0, p.highlight());

		String hexText = ColorUtil.toHex(c);
		int chevronX = x + width - 9;
		int hexX = chevronX - 8 - RenderUtil.width(hexText);
		RenderUtil.text(g, hexText, RenderUtil.Face.REGULAR, hexX, y + 5, p.textDim());
		RenderUtil.chevron(g, chevronX, y + ROW_H / 2f, e, ColorUtil.mix(p.textFaint(), p.text(), Math.max(h, e)));
		int nameX = sx + sw + 6;
		RenderUtil.text(g, RenderUtil.ellipsize(setting.name(), hexX - 6 - nameX, false), RenderUtil.Face.REGULAR, nameX, y + 5, p.text());

		if (e > 0.01f) {
			int top = y + ROW_H + 6;
			picker.setBounds(x + 8, top, ColorPicker.widthFor(SQUARE), SQUARE);
			if (fieldBelow()) {
				hex.setBounds(x + 8, top + SQUARE + 6, Math.min(100, width - 16), FIELD_H);
			} else {
				int fx = x + 8 + ColorPicker.widthFor(SQUARE) + 8;
				hex.setBounds(fx, top, Math.min(100, x + width - 8 - fx), FIELD_H);
			}
			g.enableScissor(x, y + ROW_H, x + width, y + height);
			RenderUtil.withAlpha(e, () -> super.render(g, p, mouseX, mouseY, delta));
			g.disableScissor();
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (my < y + ROW_H) {
			if (button == 0) {
				expanded = !expanded;
				if (!expanded) {
					focus(null);
				}
			}
			return true;
		}
		if (!expanded) {
			return false;
		}
		super.mouseClicked(mx, my, button);
		return true;
	}
}
