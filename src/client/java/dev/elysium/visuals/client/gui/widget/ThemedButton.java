package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/** Rounded button with smooth hover and press feedback. */
public class ThemedButton extends UiElement {
	private final Supplier<String> label;
	private final Runnable onPress;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue press = new SmoothValue(0, 20f);
	private boolean pressed;

	public ThemedButton(String label, Runnable onPress) {
		this(() -> label, onPress);
	}

	/** Button whose text can change while it is shown. */
	public ThemedButton(Supplier<String> label, Runnable onPress) {
		this.label = label;
		this.onPress = onPress;
	}

	/** Same as a setting row (used for buttons in module settings). */
	public ThemedButton rowHeight(int h) {
		this.height = h;
		return this;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) ? 1f : 0f);
		float pr = press.update(pressed ? 1f : 0f);
		// The launcher's ghost button: faint fill and border that brighten on hover, shrinks a bit when pressed.
		float inset = pr * 0.6f;
		float bx = x + inset, by = y + inset, bw = width - 2 * inset, bh = height - 2 * inset;
		RenderUtil.roundedRect(g, bx, by, bw, bh, 5, ColorUtil.withAlpha(p.text(), Math.round(0x0F + 0x0E * h)));
		RenderUtil.roundedOutline(g, bx, by, bw, bh, 5, 0, ColorUtil.mix(p.border(), ColorUtil.withAlpha(p.text(), 0x33), h));
		RenderUtil.centeredText(g, font(), RenderUtil.ellipsize(label.get(), width - 10, false), x + width / 2, y + (height - 8) / 2, p.text());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		pressed = true;
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean wasPressed = pressed;
		pressed = false;
		if (wasPressed && isHovered(mx, my)) {
			onPress.run();
			return true;
		}
		return false;
	}
}
