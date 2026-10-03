package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting.Option;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * Dropdown for a {@link ModeSetting}: the header shows the selected option and
 * expands into the list, each option with an animated radio dot.
 */
public class ModeWidget extends UiElement {
	private static final int HEADER_H = 18;
	private static final int ITEM_H = 17;
	private static final int DOT = 11;

	private final ModeSetting setting;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue expand = new SmoothValue(0, 16f);
	private final Map<String, SmoothValue> selected = new HashMap<>();
	private final Map<String, SmoothValue> itemHover = new HashMap<>();
	private boolean expanded;

	public ModeWidget(ModeSetting setting) {
		this.setting = setting;
		for (Option o : setting.options()) {
			selected.put(o.id(), new SmoothValue(setting.is(o.id()) ? 1 : 0, 16f));
			itemHover.put(o.id(), new SmoothValue(0, 22f));
		}
		this.height = HEADER_H;
	}

	private int listHeight() {
		return setting.options().size() * ITEM_H + 6;
	}

	@Override
	public int preferredHeight() {
		return HEADER_H + Math.round(expand.get() * listHeight());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float e = expand.update(expanded ? 1f : 0f);
		boolean headerHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER_H;
		float h = hover.update(headerHovered ? 1f : 0f);

		RenderUtil.well(g, x, y, width, height, 5, p, Math.max(h, e * 0.5f));
		RenderUtil.focusRing(g, x, y, width, height, 5, p, e * 0.8f);

		String current = setting.label();
		int chevronX = x + width - 9;
		int currentX = chevronX - 8 - RenderUtil.width(current);
		RenderUtil.text(g, current, RenderUtil.Face.REGULAR, currentX, y + 5, p.accent2());
		RenderUtil.chevron(g, chevronX, y + HEADER_H / 2f, e, ColorUtil.mix(p.textFaint(), p.text(), Math.max(h, e)));
		RenderUtil.text(g, RenderUtil.ellipsize(setting.name(), currentX - x - 14, false), RenderUtil.Face.REGULAR, x + 8, y + 5, p.text());

		if (e <= 0.01f) {
			return;
		}
		g.enableScissor(x, y + HEADER_H, x + width, y + height);
		RenderUtil.fadedLine(g, x + 6, y + HEADER_H, width - 12, RenderUtil.hairline(), ColorUtil.mulAlpha(p.border(), e));
		int iy = y + HEADER_H + 2;
		for (Option o : setting.options()) {
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= iy && mouseY < iy + ITEM_H;
			float ih = itemHover.get(o.id()).update(hovered ? 1f : 0f);
			float s = selected.get(o.id()).update(setting.is(o.id()) ? 1f : 0f);
			if (ih > 0.01f || s > 0.01f) {
				int bg = ColorUtil.mix(ColorUtil.withAlpha(p.text(), Math.round(0x10 * ih)), p.accentSoft(), s);
				RenderUtil.roundedRect(g, x + 3, iy, width - 6, ITEM_H - 1, 4, ColorUtil.mulAlpha(bg, e));
			}
			int textColor = ColorUtil.mix(p.textDim(), p.text(), Math.max(s, ih));
			RenderUtil.text(g, RenderUtil.ellipsize(o.label(), width - 34, false), RenderUtil.Face.REGULAR, x + 10, iy + 4,
					ColorUtil.mulAlpha(textColor, e));

			// Radio: faint ring when off, accent ring with a growing center dot when on.
			float bx = x + width - 9 - DOT, by = iy + (ITEM_H - 1 - DOT) / 2f;
			int ring = ColorUtil.mix(p.textFaint(), p.accent2(), s);
			RenderUtil.roundedOutline(g, bx, by, DOT, DOT, DOT / 2f, 1f, ColorUtil.mulAlpha(ring, e));
			float d = 5 * s;
			if (d > 0.2f) {
				RenderUtil.roundedRect(g, bx + (DOT - d) / 2f, by + (DOT - d) / 2f, d, d, d / 2f, ColorUtil.mulAlpha(p.accent2(), e));
			}
			iy += ITEM_H;
		}
		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (my < y + HEADER_H) {
			if (button == 0 || button == 1) {
				expanded = !expanded;
			}
			return true;
		}
		if (!expanded || button != 0) {
			return true;
		}
		int index = (int) ((my - (y + HEADER_H + 2)) / ITEM_H);
		if (index >= 0 && index < setting.options().size()) {
			setting.set(setting.options().get(index).id());
		}
		return true;
	}
}
