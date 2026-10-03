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
import java.util.function.IntPredicate;

/** Single-line text input with a placeholder; Enter submits. */
public class TextField extends UiElement {
	private final String placeholder;
	private final int maxLength;
	private final IntPredicate allowedChar;
	private final Consumer<String> onSubmit;
	private final SmoothValue focusAnim = new SmoothValue(0, 22f);
	private String text = "";

	public TextField(String placeholder, int maxLength, IntPredicate allowedChar, Consumer<String> onSubmit) {
		this.placeholder = placeholder;
		this.maxLength = maxLength;
		this.allowedChar = allowedChar;
		this.onSubmit = onSubmit;
		this.height = 16;
	}

	public String text() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float f = focusAnim.update(focused ? 1f : 0f);
		RenderUtil.well(g, x, y, width, height, 5, p, f);
		RenderUtil.focusRing(g, x, y, width, height, 5, p, f);
		int ty = y + (height - 8) / 2;
		if (text.isEmpty() && !focused) {
			RenderUtil.text(g, RenderUtil.ellipsize(placeholder, width - 12, false), RenderUtil.Face.REGULAR, x + 6, ty, p.textFaint());
		} else {
			// Show the end of long input.
			String shown = text;
			while (RenderUtil.width(shown) > width - 14 && !shown.isEmpty()) {
				shown = shown.substring(1);
			}
			RenderUtil.text(g, shown, RenderUtil.Face.REGULAR, x + 6, ty, p.text());
			if (focused && (Util.getMillis() / 500) % 2 == 0) {
				int cx = x + 6 + RenderUtil.width(shown) + 1;
				RenderUtil.rect(g, cx, ty - 1, RenderUtil.hairline() * 2, 10, p.accent2());
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return button == 0;
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
				clip.trim().codePoints().forEach(this::insert);
			}
			return true;
		}
		switch (event.key()) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!text.isEmpty()) {
					text = event.hasControlDown() ? "" : text.substring(0, text.length() - 1);
				}
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				onSubmit.accept(text);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		insert(event.codepoint());
		return true;
	}

	private void insert(int codepoint) {
		if (text.length() < maxLength && allowedChar.test(codepoint)) {
			text += new String(Character.toChars(codepoint));
		}
	}
}
