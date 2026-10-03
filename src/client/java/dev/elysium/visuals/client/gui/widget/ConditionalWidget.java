package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;

/**
 * Wraps a setting widget that is only shown under some condition; it smoothly
 * collapses to zero height (and ignores input) while hidden.
 */
public class ConditionalWidget extends UiContainer {
	private final UiElement child;
	private final BooleanSupplier condition;
	private final SmoothValue shown;

	public ConditionalWidget(UiElement child, BooleanSupplier condition) {
		this.child = add(child);
		this.condition = condition;
		this.shown = new SmoothValue(condition.getAsBoolean() ? 1 : 0, 14f);
	}

	/** 0 when hidden, 1 when fully shown. */
	public float fraction() {
		return shown.get();
	}

	@Override
	public int preferredHeight() {
		return Math.round(child.preferredHeight() * shown.update(condition.getAsBoolean() ? 1f : 0f));
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		child.setBounds(x, y, width, child.preferredHeight());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float s = shown.get();
		if (s < 0.01f) {
			return;
		}
		if (s > 0.99f) {
			child.render(g, p, mouseX, mouseY, delta);
			return;
		}
		g.enableScissor(x, y, x + width, y + height);
		RenderUtil.withAlpha(s, () -> child.render(g, p, mouseX, mouseY, delta));
		g.disableScissor();
	}

	@Override
	public boolean isHovered(double mx, double my) {
		return condition.getAsBoolean() && super.isHovered(mx, my);
	}
}
