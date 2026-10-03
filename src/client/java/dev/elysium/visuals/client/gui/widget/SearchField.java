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

import java.util.function.Consumer;

/** Module search box in the window header: magnifier, placeholder, live filtering and a clear button. */
public class SearchField extends UiElement {
	private static final int MAX_LENGTH = 32;

	private final Consumer<String> onChange;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue focusAnim = new SmoothValue(0, 22f);
	private String text = "";

	public SearchField(Consumer<String> onChange) {
		this.onChange = onChange;
		this.height = 16;
	}

	public String text() {
		return text;
	}

	public void clear() {
		setText("");
	}

	private void setText(String next) {
		if (!next.equals(text)) {
			text = next;
			onChange.accept(text);
		}
	}

	private boolean overClear(double mx, double my) {
		return !text.isEmpty() && mx >= x + width - 14 && mx < x + width && my >= y && my < y + height;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) ? 1f : 0f);
		float f = focusAnim.update(focused ? 1f : 0f);
		RenderUtil.well(g, x, y, width, height, 6, p, Math.max(h * 0.6f, f));
		RenderUtil.focusRing(g, x, y, width, height, 6, p, f);

		RenderUtil.searchIcon(g, x + 9, y + height / 2f - 0.5f, ColorUtil.mix(p.textFaint(), p.accent2(), f));
		int tx = x + 17;
		int ty = y + (height - 8) / 2;
		int room = width - 17 - (text.isEmpty() ? 6 : 16);
		if (text.isEmpty()) {
			RenderUtil.text(g, RenderUtil.ellipsize("Поиск модулей", room, false), RenderUtil.Face.REGULAR, tx, ty, p.textFaint());
		} else {
			// Show the end of long input.
			String shown = text;
			while (RenderUtil.width(shown) > room && !shown.isEmpty()) {
				shown = shown.substring(1);
			}
			RenderUtil.text(g, shown, RenderUtil.Face.REGULAR, tx, ty, p.text());
			boolean overX = overClear(mouseX, mouseY);
			RenderUtil.cross(g, x + width - 8, y + height / 2f, 2f, overX ? p.text() : p.textFaint());
		}
		if (focused && (Util.getMillis() / 500) % 2 == 0) {
			int cx = tx + (text.isEmpty() ? 0 : Math.min(RenderUtil.width(text), room)) + 1;
			RenderUtil.rect(g, cx, ty - 1, RenderUtil.hairline() * 2, 10, p.accent2());
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 1 || (button == 0 && overClear(mx, my))) {
			clear();
		}
		return button == 0 || button == 1;
	}

	@Override
	public boolean isFocusable() {
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isPaste()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) {
				StringBuilder next = new StringBuilder(text);
				clip.trim().codePoints().filter(c -> c >= 32 && c != 127).forEach(next::appendCodePoint);
				setText(next.length() > MAX_LENGTH ? next.substring(0, MAX_LENGTH) : next.toString());
			}
			return true;
		}
		switch (event.key()) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!text.isEmpty()) {
					setText(event.hasControlDown() ? "" : text.substring(0, text.length() - 1));
				}
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
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
		int cp = event.codepoint();
		if (cp < 32 || cp == 127 || text.length() >= MAX_LENGTH) {
			return true;
		}
		setText(text + new String(Character.toChars(cp)));
		return true;
	}
}
