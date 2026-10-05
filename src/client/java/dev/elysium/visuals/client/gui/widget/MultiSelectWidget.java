package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting.Option;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * Dropdown for a {@link MultiSelectSetting}: a header with "N из M" that
 * expands into a list of options, each with an animated check box on the right.
 */
public class MultiSelectWidget extends UiElement {
	private static final int HEADER_H = 18;
	private static final int ITEM_H = 17;
	private static final int BOX = 11;

	private final MultiSelectSetting setting;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue expand = new SmoothValue(0, 16f);
	private final Map<String, SmoothValue> checks = new HashMap<>();
	private final Map<String, SmoothValue> itemHover = new HashMap<>();
	private boolean expanded;

	public MultiSelectWidget(MultiSelectSetting setting) {
		this.setting = setting;
		for (Option o : setting.options()) {
			checks.put(o.id(), new SmoothValue(setting.isSelected(o.id()) ? 1 : 0, 16f));
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

		long active = setting.options().stream().filter(o -> setting.isActive(o.id())).count();
		long usable = setting.options().stream().filter(o -> setting.disabledReason(o.id()) == null).count();
		String summary = active + " из " + usable;
		int chevronX = x + width - 9;
		int summaryX = chevronX - 8 - RenderUtil.width(summary);
		RenderUtil.text(g, summary, RenderUtil.Face.REGULAR, summaryX, y + 5, p.accent2());
		RenderUtil.chevron(g, chevronX, y + HEADER_H / 2f, e, ColorUtil.mix(p.textFaint(), p.text(), Math.max(h, e)));
		RenderUtil.text(g, RenderUtil.ellipsize(setting.name(), summaryX - x - 14, false), RenderUtil.Face.REGULAR, x + 8, y + 5, p.text());

		if (e <= 0.01f) {
			return;
		}
		g.enableScissor(x, y + HEADER_H, x + width, y + height);
		RenderUtil.fadedLine(g, x + 6, y + HEADER_H, width - 12, RenderUtil.hairline(), ColorUtil.mulAlpha(p.border(), e));
		int iy = y + HEADER_H + 2;
		for (Option o : setting.options()) {
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= iy && mouseY < iy + ITEM_H;
			float ih = itemHover.get(o.id()).update(hovered ? 1f : 0f);
			float c = checks.get(o.id()).update(setting.isSelected(o.id()) ? 1f : 0f);
			if (ih > 0.01f) {
				RenderUtil.roundedRect(g, x + 3, iy, width - 6, ITEM_H - 1, 4, ColorUtil.withAlpha(p.text(), Math.round(0x10 * ih * e)));
			}

			String reason = setting.disabledReason(o.id());
			if (reason != null) {
				// Greyed out: the label and, on the right, who already does it.
				int rw = Math.min(RenderUtil.width(reason, RenderUtil.Face.SMALL), (width - 20) / 2);
				RenderUtil.text(g, RenderUtil.ellipsize(o.label(), width - 28 - rw, false), RenderUtil.Face.REGULAR, x + 10, iy + 4,
						ColorUtil.mulAlpha(p.textFaint(), e));
				RenderUtil.text(g, RenderUtil.ellipsize(reason, rw, RenderUtil.Face.SMALL), RenderUtil.Face.SMALL, x + width - 9 - rw, iy + 5,
						ColorUtil.mulAlpha(p.textFaint(), e));
				iy += ITEM_H;
				continue;
			}
			int textColor = ColorUtil.mix(p.textDim(), p.text(), Math.max(c, ih));
			RenderUtil.text(g, RenderUtil.ellipsize(o.label(), width - 34, false), RenderUtil.Face.REGULAR, x + 10, iy + 4,
					ColorUtil.mulAlpha(textColor, e));

			// Check box: faint outline when off, accent gradient with an animated tick when on.
			float bx = x + width - 9 - BOX, by = iy + (ITEM_H - 1 - BOX) / 2f;
			int boxOutline = ColorUtil.mix(p.textFaint(), p.accent2(), c);
			RenderUtil.roundedGradient(g, bx, by, BOX, BOX, 3, ColorUtil.mulAlpha(p.accent2(), c * e), ColorUtil.mulAlpha(p.accent(), c * e));
			RenderUtil.roundedOutline(g, bx, by, BOX, BOX, 3, 1f, ColorUtil.mulAlpha(boxOutline, e * (1f - 0.6f * c)));
			RenderUtil.checkmark(g, bx + BOX / 2f, by + BOX / 2f, c, ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * e)));
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
		if (index >= 0 && index < setting.options().size() && setting.disabledReason(setting.options().get(index).id()) == null) {
			setting.toggle(setting.options().get(index).id());
		}
		return true;
	}
}
