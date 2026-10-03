package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.Deque;

/** WASD, mouse buttons with clicks per second and the space bar, lit up while pressed. */
public class Keystrokes extends Module {
	private final NumberSetting keySize = add(new NumberSetting("key_size", "Размер клавиш", 20, 14, 26, 1));
	private final BooleanSetting mouse = add(new BooleanSetting("mouse", "Кнопки мыши", true));
	private final BooleanSetting cps = add(new BooleanSetting("cps", "Клики в секунду", true));
	private final BooleanSetting space = add(new BooleanSetting("space", "Пробел", true));
	private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", "Цвет нажатия", 0xFF8FDBFF));

	public Keystrokes() {
		super("keystrokes", "Keystrokes", "Показывает нажатые клавиши и CPS", Category.RENDER);
		addHud(new Element());
	}

	/** One key: press animation and, for mouse buttons, recent click times. */
	private static final class Key {
		final String label;
		final SmoothValue press = new SmoothValue(0, 18f);
		final Deque<Long> clicks = new ArrayDeque<>();
		boolean wasDown;

		Key(String label) {
			this.label = label;
		}

		float update(KeyMapping mapping) {
			boolean down = mapping.isDown();
			long now = Util.getMillis();
			if (down && !wasDown) {
				clicks.addLast(now);
			}
			wasDown = down;
			while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) {
				clicks.removeFirst();
			}
			return press.update(down ? 1f : 0f);
		}
	}

	private final Key w = new Key("W"), a = new Key("A"), s = new Key("S"), d = new Key("D");
	private final Key lmb = new Key("ЛКМ"), rmb = new Key("ПКМ"), jump = new Key("");

	private final class Element extends HudElement {
		private static final int GAP = 2;

		Element() {
			super("keys", "Клавиши", Anchor.TOP_LEFT);
		}

		@Override
		public boolean hasContent() {
			return Minecraft.getInstance().player != null;
		}

		@Override
		protected void measure(boolean preview) {
			int k = keySize.intValue();
			width = k * 3 + GAP * 2 + HudStyle.PAD * 2 - 4;
			int rows = 2 + (mouse.isOn() ? 1 : 0);
			int extra = mouse.isOn() && cps.isOn() ? 4 : 0;
			height = rows * k + extra + (rows - 1) * GAP + (space.isOn() ? GAP + k / 2 : 0) + HudStyle.PAD * 2 - 4;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			Options o = Minecraft.getInstance().options;
			HudStyle.panel(g, p, x, y, width, height);
			int k = keySize.intValue();
			int left = x + HudStyle.PAD - 2, top = y + HudStyle.PAD - 2;
			key(g, p, w, w.update(o.keyUp), left + k + GAP, top, k, k, null);
			top += k + GAP;
			key(g, p, a, a.update(o.keyLeft), left, top, k, k, null);
			key(g, p, s, s.update(o.keyDown), left + k + GAP, top, k, k, null);
			key(g, p, d, d.update(o.keyRight), left + (k + GAP) * 2, top, k, k, null);
			top += k + GAP;
			if (mouse.isOn()) {
				int mw = (k * 3 + GAP) / 2;
				float lp = lmb.update(o.keyAttack), rp = rmb.update(o.keyUse);
				int mh = cps.isOn() ? k + 4 : k;
				key(g, p, lmb, lp, left, top, mw, mh, cps.isOn() ? cpsText(lmb, mw) : null);
				key(g, p, rmb, rp, left + mw + GAP, top, mw, mh, cps.isOn() ? cpsText(rmb, mw) : null);
				top += mh + GAP;
			}
			if (space.isOn()) {
				int sw = k * 3 + GAP * 2;
				float sp = jump.update(o.keyJump);
				keyBox(g, p, sp, left, top, sw, k / 2);
				RenderUtil.roundedRect(g, left + sw / 2f - 8, top + k / 4f - 0.5f, 16, 1, 0.5f,
						ColorUtil.mix(p.textDim(), 0xFFFFFFFF, sp));
			}
		}

		/** "5 CPS", or just "5" when the key is too narrow. */
		private String cpsText(Key key, int keyWidth) {
			String full = key.clicks.size() + " CPS";
			return RenderUtil.width(full) + 6 <= keyWidth ? full : String.valueOf(key.clicks.size());
		}

		private void keyBox(GuiGraphicsExtractor g, Palette p, float pressed, int kx, int ky, int kw, int kh) {
			int idle = ColorUtil.mulAlpha(p.text(), 0.08f);
			int on = ColorUtil.mulAlpha(pressedColor.argb(), 0.85f);
			RenderUtil.roundedRect(g, kx, ky, kw, kh, 4, ColorUtil.mix(idle, on, pressed));
		}

		private void key(GuiGraphicsExtractor g, Palette p, Key key, float pressed, int kx, int ky, int kw, int kh, String sub) {
			keyBox(g, p, pressed, kx, ky, kw, kh);
			int color = ColorUtil.mix(p.text(), 0xFFFFFFFF, pressed);
			if (sub == null) {
				RenderUtil.textBold(g, key.label, kx + (kw - RenderUtil.widthBold(key.label)) / 2, ky + (kh - 8) / 2 + 1, color);
			} else {
				RenderUtil.textBold(g, key.label, kx + (kw - RenderUtil.widthBold(key.label)) / 2, ky + kh / 2 - 8, color);
				RenderUtil.text(g, null, sub, kx + (kw - RenderUtil.width(sub)) / 2, ky + kh / 2 + 1, ColorUtil.mix(p.textDim(), color, pressed));
			}
		}
	}
}
