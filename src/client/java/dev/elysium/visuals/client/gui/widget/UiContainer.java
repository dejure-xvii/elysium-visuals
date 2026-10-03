package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** An element holding children; dispatches input and tracks focus and dragging. */
public class UiContainer extends UiElement {
	protected final List<UiElement> children = new ArrayList<>();
	private UiElement focusedChild;
	private UiElement draggingChild;

	public <T extends UiElement> T add(T child) {
		children.add(child);
		return child;
	}

	public void clear() {
		children.clear();
		focusedChild = null;
		draggingChild = null;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		for (UiElement child : children) {
			child.render(g, p, mouseX, mouseY, delta);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (int i = children.size() - 1; i >= 0; i--) {
			UiElement child = children.get(i);
			if (child.isHovered(mx, my) && child.mouseClicked(mx, my, button)) {
				focus(child.isFocusable() ? child : null);
				draggingChild = child;
				return true;
			}
		}
		focus(null);
		return false;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		UiElement d = draggingChild;
		draggingChild = null;
		return d != null && d.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		return draggingChild != null && draggingChild.mouseDragged(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		for (int i = children.size() - 1; i >= 0; i--) {
			UiElement child = children.get(i);
			if (child.isHovered(mx, my) && child.mouseScrolled(mx, my, amount)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return hasKeyboardFocus() && focusedChild.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return hasKeyboardFocus() && focusedChild.charTyped(event);
	}

	// A container is "focused" while one of its children captures the keyboard,
	// so focus works through nested containers (tab → module card → text field).
	@Override
	public boolean isFocusable() {
		return hasKeyboardFocus();
	}

	@Override
	public boolean isFocused() {
		return hasKeyboardFocus();
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) {
			focus(null);
		}
	}

	/** True if a child (e.g. a text field) currently captures the keyboard. */
	public boolean hasKeyboardFocus() {
		// A child may drop focus by itself (e.g. a text field on Enter).
		if (focusedChild != null && !focusedChild.isFocused()) {
			focusedChild = null;
		}
		return focusedChild != null;
	}

	public void focus(UiElement child) {
		if (focusedChild == child) {
			return;
		}
		if (focusedChild != null) {
			focusedChild.setFocused(false);
		}
		focusedChild = child;
		if (child != null) {
			child.setFocused(true);
		}
	}
}
