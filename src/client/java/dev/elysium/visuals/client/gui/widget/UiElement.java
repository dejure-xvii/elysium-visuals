package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Base class for the ClickGUI's lightweight widgets. Coordinates are in GUI
 * units; mouse coordinates passed in are already in the element's space.
 */
public abstract class UiElement {
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	protected boolean focused;

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	/**
	 * Height this element wants in a vertical list. Elements that expand
	 * (module cards, color settings) override it with an animated value.
	 */
	public int preferredHeight() {
		return height;
	}

	public abstract void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta);

	public boolean isHovered(double mx, double my) {
		return mx >= x && mx < x + width && my >= y && my < y + height;
	}

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return false;
	}

	public boolean mouseDragged(double mx, double my, int button) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	/** Whether clicking this element should give it keyboard focus. */
	public boolean isFocusable() {
		return false;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	public boolean isFocused() {
		return focused;
	}

	protected static Font font() {
		return Minecraft.getInstance().font;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}
}
