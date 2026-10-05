package dev.elysium.visuals.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.KeySetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Key bind of a module (or a {@link KeySetting}). Click, then press a key;
 * Backspace/Delete removes the bind, Escape cancels.
 */
public class BindButton extends UiElement {
	private final String label;
	private final IntSupplier getter;
	private final IntConsumer setter;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue listen = new SmoothValue(0, 22f);

	public BindButton(Module module) {
		this("Клавиша", module::bind, module::setBind);
	}

	public BindButton(KeySetting setting) {
		this(setting.name(), setting::key, setting::set);
	}

	private BindButton(String label, IntSupplier getter, IntConsumer setter) {
		this.label = label;
		this.getter = getter;
		this.setter = setter;
		this.height = 18;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float h = hover.update(isHovered(mouseX, mouseY) ? 1f : 0f);
		float l = listen.update(focused ? 1f : 0f);
		RenderUtil.well(g, x, y, width, height, 5, p, Math.max(h, l * 0.5f));
		RenderUtil.text(g, label, RenderUtil.Face.REGULAR, x + 8, y + 5, p.text());

		String key;
		if (focused) {
			key = "Нажмите клавишу…";
		} else if (getter.getAsInt() == Module.NO_KEY) {
			key = "Нет";
		} else {
			key = InputConstants.Type.KEYSYM.getOrCreate(getter.getAsInt()).getDisplayName().getString();
		}
		int keyW = RenderUtil.width(key) + 12;
		int kx = x + width - 3 - keyW;
		int pill = ColorUtil.mix(ColorUtil.withAlpha(p.text(), 0x14), p.accentSoft(), l);
		RenderUtil.roundedRect(g, kx, y + 3, keyW, height - 6, 4, pill);
		RenderUtil.roundedOutline(g, kx, y + 3, keyW, height - 6, 4, 0, p.border());
		if (focused) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 180.0);
			RenderUtil.roundedOutline(g, kx, y + 3, keyW, height - 6, 4, 0, ColorUtil.mulAlpha(p.accent2(), 0.4f + 0.5f * pulse));
		}
		int keyColor = getter.getAsInt() == Module.NO_KEY && !focused ? p.textFaint() : (focused ? p.accent2() : p.text());
		RenderUtil.text(g, key, RenderUtil.Face.REGULAR, kx + 6, y + 5, keyColor);
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
		int key = event.key();
		if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
			setter.accept(Module.NO_KEY);
		} else if (key != GLFW.GLFW_KEY_UNKNOWN) {
			setter.accept(key);
		}
		setFocused(false);
		return true;
	}
}
