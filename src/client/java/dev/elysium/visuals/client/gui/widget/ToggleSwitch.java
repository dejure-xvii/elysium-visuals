package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Label + animated on/off switch in a setting row. */
public class ToggleSwitch extends UiElement {
	private static final int TRACK_W = 18;
	private static final int TRACK_H = 10;

	private final String label;
	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;
	private final SmoothValue knob;
	private final SmoothValue hover = new SmoothValue(0, 22f);

	public ToggleSwitch(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		this.label = label;
		this.getter = getter;
		this.setter = setter;
		this.knob = new SmoothValue(getter.getAsBoolean() ? 1 : 0, 18f);
		this.height = 18;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) ? 1f : 0f);
		float k = knob.update(getter.getAsBoolean() ? 1f : 0f);

		RenderUtil.well(g, x, y, width, height, 5, p, h);
		int tx = x + width - TRACK_W - 5;
		String shown = RenderUtil.ellipsize(label, tx - x - 8 - 6, false);
		RenderUtil.text(g, shown, RenderUtil.Face.REGULAR, x + 8, y + (height - 8) / 2, p.text());
		RenderUtil.toggle(g, tx, y + (height - TRACK_H) / 2f, TRACK_W, TRACK_H, p, k, h);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		setter.accept(!getter.getAsBoolean());
		return true;
	}
}
