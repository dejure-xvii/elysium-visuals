package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Slider for a {@link NumberSetting}: label and value on top, a thin track
 * below that fills with the lilac → violet gradient, and a white knob with an
 * accent ring (the launcher's range input).
 */
public class SliderWidget extends UiElement {
	private static final int PAD = 8;

	private final NumberSetting setting;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue knob = new SmoothValue(0, 22f);
	private final SmoothValue fill;
	private boolean dragging;

	public SliderWidget(NumberSetting setting) {
		this.setting = setting;
		this.fill = new SmoothValue((float) setting.fraction(), 22f);
		this.height = 28;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) || dragging ? 1f : 0f);
		float k = knob.update(dragging ? 1f : 0f);
		float f = fill.update((float) setting.fraction());

		RenderUtil.well(g, x, y, width, height, 5, p, h);
		String value = setting.display();
		int valueX = x + width - PAD - RenderUtil.widthBold(value);
		RenderUtil.textBold(g, value, valueX, y + 5, p.accent2());
		RenderUtil.text(g, RenderUtil.ellipsize(setting.name(), valueX - x - PAD - 6, false), RenderUtil.Face.REGULAR,
				x + PAD, y + 5, p.text());

		float trackX = x + PAD, trackW = width - PAD * 2, trackY = y + height - 8, trackH = 3;
		RenderUtil.roundedRect(g, trackX, trackY, trackW, trackH, trackH / 2f, ColorUtil.withAlpha(p.text(), 0x1A));
		float fw = Math.max(trackH, trackW * f);
		RenderUtil.roundedHGradient(g, trackX, trackY, fw, trackH, trackH / 2f, p.accent2(), p.accent());

		float r = 3.5f + k * 0.5f + h * 0.25f;
		float kx = trackX + trackW * f;
		float ky = trackY + trackH / 2f;
		RenderUtil.roundedRect(g, kx - r - 2, ky - r - 2, (r + 2) * 2, (r + 2) * 2, r + 2, ColorUtil.mulAlpha(p.accentSoft(), 0.6f + 0.4f * h));
		RenderUtil.softGlow(g, kx - r, ky - r, r * 2, r * 2, r, 0x50000000, 2);
		RenderUtil.roundedRect(g, kx - r, ky - r, r * 2, r * 2, r, 0xFFFFFFFF);
		RenderUtil.roundedOutline(g, kx - r, ky - r, r * 2, r * 2, r, 1.6f, p.accent());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		dragging = true;
		update(mx);
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		if (!dragging) {
			return false;
		}
		update(mx);
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	private void update(double mx) {
		setting.setFraction((mx - (x + PAD)) / (width - PAD * 2.0));
	}
}
