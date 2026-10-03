package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntSupplier;

/** Large swatch showing a color (with a checkerboard behind transparency). */
public class ColorPreview extends UiElement {
	private final IntSupplier color;

	public ColorPreview(IntSupplier color) {
		this.color = color;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		int c = color.getAsInt();
		if (ColorUtil.alpha(c) < 0xFF) {
			RenderUtil.checkerboard(g, x + 2, y + 1, width - 4, height - 2, 4);
		}
		RenderUtil.softGlow(g, x, y, width, height, 5, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 0xFF), 0.3f), 4);
		RenderUtil.roundedRect(g, x, y, width, height, 5, c);
		RenderUtil.roundedOutline(g, x, y, width, height, 5, 0, p.highlight());
	}
}
