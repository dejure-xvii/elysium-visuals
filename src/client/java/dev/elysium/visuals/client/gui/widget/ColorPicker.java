package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * HSV color picker: saturation/value square, vertical hue slider and vertical
 * alpha slider. Reads its color from {@code source} every frame, so changes
 * made elsewhere (HEX field, switching the edited slot) show up immediately.
 */
public class ColorPicker extends UiElement {
	public static final int SLIDER_W = 10;
	public static final int GAP = 6;

	private enum Drag { NONE, SV, HUE, ALPHA }

	private final IntSupplier source;
	private final IntConsumer onChange;
	private float hue, sat, val, alpha = 1f;
	private int lastColor;
	private boolean synced;
	private Drag drag = Drag.NONE;

	public ColorPicker(IntSupplier source, IntConsumer onChange) {
		this.source = source;
		this.onChange = onChange;
	}

	/** Total width for a square of the given size. */
	public static int widthFor(int squareSize) {
		return squareSize + 2 * (GAP + SLIDER_W);
	}

	private int square() {
		return height;
	}

	private int hueX() {
		return x + square() + GAP;
	}

	private int alphaX() {
		return hueX() + SLIDER_W + GAP;
	}

	private void syncFromSource() {
		int c = source.getAsInt();
		if (synced && c == lastColor) {
			return;
		}
		float[] hsv = ColorUtil.rgbToHsv(c);
		// Keep the current hue when it is undefined (greys) so the square doesn't jump.
		if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
			hue = hsv[0];
		}
		if (hsv[2] > 0.001f) {
			sat = hsv[1];
		}
		val = hsv[2];
		alpha = ColorUtil.alpha(c) / 255f;
		lastColor = c;
		synced = true;
	}

	private int currentColor() {
		return ColorUtil.withAlpha(ColorUtil.hsvToRgb(hue, sat, val), Math.round(alpha * 255));
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		syncFromSource();
		int size = square();

		// Saturation/value square as two exact linear gradients:
		// white → pure hue horizontally, then transparent → black vertically on top.
		// The square: white → pure hue horizontally, transparent → black vertically, with rounded corners.
		int pure = ColorUtil.hsvToRgb(hue, 1f, 1f);
		RenderUtil.roundedCorners(g, x, y, size, size, 5, 0xFFFFFFFF, pure, 0xFFFFFFFF, pure);
		RenderUtil.roundedCorners(g, x, y, size, size, 5, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
		RenderUtil.roundedOutline(g, x, y, size, size, 5, 0, p.border());

		// SV handle: white ring with a dark hairline so it reads on any color.
		float hx = x + sat * size;
		float hy = y + (1 - val) * size;
		RenderUtil.softGlow(g, hx - 3.5f, hy - 3.5f, 7, 7, 3.5f, 0x60000000, 2);
		RenderUtil.roundedOutline(g, hx - 3.5f, hy - 3.5f, 7, 7, 3.5f, 1.5f, 0xFFFFFFFF);

		// Hue slider: six gradient segments through the color wheel, clipped to a pill.
		int hx0 = hueX();
		// The two end segments are rounded and extended inwards; the middle ones are drawn over the extensions.
		float seg = size / 6f, ext = SLIDER_W;
		float extHue = ext / size;
		RenderUtil.roundedGradient(g, hx0, y, SLIDER_W, seg + ext, SLIDER_W / 2f,
				ColorUtil.hsvToRgb(0f, 1, 1), ColorUtil.hsvToRgb(1 / 6f + extHue, 1, 1));
		RenderUtil.roundedGradient(g, hx0, y + 5 * seg - ext, SLIDER_W, seg + ext, SLIDER_W / 2f,
				ColorUtil.hsvToRgb(5 / 6f - extHue, 1, 1), ColorUtil.hsvToRgb(0.9999f, 1, 1));
		for (int i = 1; i < 5; i++) {
			RenderUtil.roundedGradient(g, hx0, y + seg * i, SLIDER_W, seg, 0,
					ColorUtil.hsvToRgb(i / 6f, 1, 1), ColorUtil.hsvToRgb((i + 1) / 6f, 1, 1));
		}
		RenderUtil.roundedOutline(g, hx0, y, SLIDER_W, size, SLIDER_W / 2f, 0, p.border());
		sliderHandle(g, hx0, y + hue * size, size);

		// Alpha slider: opaque at the top, transparent at the bottom, over a checkerboard.
		int ax0 = alphaX();
		// Checkerboard only along the straight part; the rounded ends stay clean.
		RenderUtil.checkerboard(g, ax0 + 1, y + SLIDER_W / 2, SLIDER_W - 2, size - SLIDER_W, 4);
		int opaque = ColorUtil.hsvToRgb(hue, sat, val);
		RenderUtil.roundedGradient(g, ax0, y, SLIDER_W, size, SLIDER_W / 2f, opaque, ColorUtil.withAlpha(opaque, 0));
		RenderUtil.roundedOutline(g, ax0, y, SLIDER_W, size, SLIDER_W / 2f, 0, p.border());
		sliderHandle(g, ax0, y + (1 - alpha) * size, size);
	}

	/** White pill across a vertical slider, kept inside its ends. */
	private void sliderHandle(GuiGraphicsExtractor g, int sx, float cy, int size) {
		float c = Math.max(y + 2f, Math.min(y + size - 2f, cy));
		RenderUtil.softGlow(g, sx - 1, c - 2, SLIDER_W + 2, 4, 2, 0x60000000, 2);
		RenderUtil.roundedOutline(g, sx - 1, c - 2, SLIDER_W + 2, 4, 2, 1.2f, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		syncFromSource();
		int size = square();
		if (mx < x + size) {
			drag = Drag.SV;
		} else if (mx >= hueX() - GAP / 2.0 && mx < hueX() + SLIDER_W + GAP / 2.0) {
			drag = Drag.HUE;
		} else if (mx >= alphaX() - GAP / 2.0) {
			drag = Drag.ALPHA;
		} else {
			return false;
		}
		update(mx, my);
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		if (drag == Drag.NONE) {
			return false;
		}
		update(mx, my);
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = drag != Drag.NONE;
		drag = Drag.NONE;
		return was;
	}

	private void update(double mx, double my) {
		int size = square();
		float fx = clamp01((float) (mx - x) / size);
		float fy = clamp01((float) (my - y) / size);
		switch (drag) {
			case SV -> {
				sat = fx;
				val = 1 - fy;
			}
			case HUE -> hue = Math.min(fy, 0.9999f);
			case ALPHA -> alpha = 1 - fy;
			default -> {
				return;
			}
		}
		int c = currentColor();
		lastColor = c;
		onChange.accept(c);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
