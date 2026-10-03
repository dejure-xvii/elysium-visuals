package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Text field for a HEX color ("#RRGGBB" or "#AARRGGBB"). Valid input is
 * applied while typing; invalid input is highlighted in red.
 */
public class HexField extends UiElement {
	private static final int MAX_LENGTH = 9;
	private static final int ERROR_COLOR = Palette.DANGER;

	private final IntSupplier source;
	private final IntConsumer onChange;
	private final SmoothValue focusAnim = new SmoothValue(0, 22f);
	private String text = "";
	private int shownColor;
	private boolean selectAll;

	public HexField(IntSupplier source, IntConsumer onChange) {
		this.source = source;
		this.onChange = onChange;
		this.shownColor = source.getAsInt();
		this.text = ColorUtil.toHex(shownColor);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		int c = source.getAsInt();
		if (!focused && c != shownColor) {
			shownColor = c;
			text = ColorUtil.toHex(c);
		}
		float f = focusAnim.update(focused ? 1f : 0f);
		boolean valid = ColorUtil.parseHex(text) != null;

		RenderUtil.well(g, x, y, width, height, 5, p, f);
		if (valid) {
			RenderUtil.focusRing(g, x, y, width, height, 5, p, f);
		} else {
			RenderUtil.roundedOutline(g, x, y, width, height, 5, 0, ColorUtil.mulAlpha(ERROR_COLOR, 0.7f));
		}

		int ty = y + (height - 8) / 2;
		int tx = x + 6;
		if (selectAll && focused) {
			RenderUtil.roundedRect(g, tx - 1, ty - 1, RenderUtil.width(text) + 2, 10, 2, ColorUtil.mulAlpha(p.accent(), 0.45f));
		}
		RenderUtil.text(g, text, RenderUtil.Face.REGULAR, tx, ty, valid ? p.text() : ERROR_COLOR);
		if (focused && (Util.getMillis() / 500) % 2 == 0) {
			int cx = tx + RenderUtil.width(text) + 1;
			RenderUtil.rect(g, cx, ty - 1, RenderUtil.hairline() * 2, 10, p.accent2());
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		// First click selects everything so typing replaces the value; a second click deselects.
		selectAll = !focused;
		return button == 0;
	}

	@Override
	public boolean isFocusable() {
		return true;
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) {
			selectAll = false;
			// Revert unfinished/invalid input to the actual color.
			shownColor = source.getAsInt();
			text = ColorUtil.toHex(shownColor);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isSelectAll()) {
			selectAll = true;
			return true;
		}
		if (event.isPaste()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) {
				clip = clip.trim();
				// A complete HEX color replaces the field; anything else is appended.
				if (ColorUtil.parseHex(clip) != null) {
					selectAll = true;
				}
				replaceOrAppend(clip);
			}
			return true;
		}
		if (event.isCopy()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(text);
			return true;
		}
		switch (event.key()) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (selectAll) {
					text = "";
					selectAll = false;
				} else if (!text.isEmpty()) {
					text = text.substring(0, text.length() - 1);
				}
				applyIfValid();
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				applyIfValid();
				setFocused(false);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String s = event.codepointAsString();
		if (s.length() != 1 || "#0123456789abcdefABCDEF".indexOf(s.charAt(0)) < 0) {
			return false;
		}
		replaceOrAppend(s);
		return true;
	}

	private void replaceOrAppend(String s) {
		String next = selectAll ? s : text + s;
		selectAll = false;
		next = next.toUpperCase(Locale.ROOT);
		if (!next.startsWith("#")) {
			next = "#" + next.replace("#", "");
		} else {
			next = "#" + next.substring(1).replace("#", "");
		}
		if (next.length() > MAX_LENGTH) {
			next = next.substring(0, MAX_LENGTH);
		}
		text = next;
		applyIfValid();
	}

	private void applyIfValid() {
		// Only 6/8 digit values are applied live; "#RGB" would fire while typing "#RRGGBB".
		String digits = text.startsWith("#") ? text.substring(1) : text;
		if (digits.length() != 6 && digits.length() != 8) {
			return;
		}
		Integer c = ColorUtil.parseHex(text);
		if (c != null) {
			shownColor = c;
			onChange.accept(c);
		}
	}
}
