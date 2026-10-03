package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.Setting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A module in the list: a glass card with the name, a short muted description
 * and an on/off switch on the right. Clicking the card (or right-clicking
 * anywhere) expands it to show the module's settings and key bind.
 */
public class ModuleCard extends UiContainer {
	private static final int HEADER = 32;
	private static final int RADIUS = 8;
	private static final int TRACK_W = 20;
	private static final int TRACK_H = 11;
	private static final int GAP = 4;

	private final Module module;
	private final boolean showCategory;
	private final SmoothValue hover = new SmoothValue(0, 22f);
	private final SmoothValue switchHover = new SmoothValue(0, 22f);
	private final SmoothValue enabledAnim;
	private final SmoothValue expand = new SmoothValue(0, 16f);
	private boolean expanded;
	private int bodyHeight;

	public ModuleCard(Module module) {
		this(module, false);
	}

	/** @param showCategory show the category next to the name (search results) */
	public ModuleCard(Module module, boolean showCategory) {
		this.module = module;
		this.showCategory = showCategory;
		this.enabledAnim = new SmoothValue(module.isEnabled() ? 1 : 0, 18f);
		for (Setting<?> setting : module.settings()) {
			add(new ConditionalWidget(SettingWidgets.create(setting), setting::isVisible));
		}
		add(new BindButton(module));
		this.height = HEADER;
	}

	@Override
	public int preferredHeight() {
		return HEADER + Math.round(expand.get() * bodyHeight);
	}

	private int switchX() {
		return x + width - TRACK_W - 11;
	}

	private int switchY() {
		return y + (HEADER - TRACK_H) / 2;
	}

	private boolean overSwitch(double mx, double my) {
		return mx >= switchX() - 4 && mx < switchX() + TRACK_W + 4 && my >= y && my < y + HEADER;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		float e = expand.update(expanded ? 1f : 0f);
		boolean headerHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER;
		float h = hover.update(headerHovered ? 1f : 0f);
		float sh = switchHover.update(overSwitch(mouseX, mouseY) ? 1f : 0f);
		float on = enabledAnim.update(module.isEnabled() ? 1f : 0f);

		// Lay out settings below the header (positions follow animated heights).
		int cy = y + HEADER + 2;
		for (var child : children) {
			child.setBounds(x + 8, cy, width - 16, child.preferredHeight());
			// Hidden settings collapse together with the gap after them.
			float shown = child instanceof ConditionalWidget c ? c.fraction() : 1f;
			cy += child.height() + Math.round(GAP * shown);
		}
		bodyHeight = cy - (y + HEADER) + 4;

		RenderUtil.glass(g, x, y, width, height, RADIUS, p, h * 0.7f, on * 0.8f);

		int textX = x + 11;
		int textRight = switchX() - 16;
		int nameColor = ColorUtil.mix(p.text(), p.isDark() ? 0xFFFFFFFF : p.text(), on);
		String name = RenderUtil.ellipsize(module.name(), textRight - textX, true);
		RenderUtil.textBold(g, name, textX, y + 7, nameColor);
		if (showCategory) {
			int cx = textX + RenderUtil.widthBold(name) + 6;
			String cat = module.category().displayName();
			if (cx + RenderUtil.captionWidth(cat) <= textRight) {
				RenderUtil.caption(g, cat, cx, y + 7, p.textFaint());
			}
		}
		RenderUtil.text(g, RenderUtil.ellipsize(module.description(), textRight - textX, false), RenderUtil.Face.REGULAR,
				textX, y + 18, p.textDim());

		RenderUtil.chevron(g, switchX() - 8, y + HEADER / 2f, e, ColorUtil.mix(p.textFaint(), p.textDim(), h));
		RenderUtil.toggle(g, switchX(), switchY(), TRACK_W, TRACK_H, p, on, sh);

		if (e > 0.01f) {
			RenderUtil.fadedLine(g, x + 8, y + HEADER - 1, width - 16, RenderUtil.hairline(), ColorUtil.mulAlpha(p.border(), e));
			g.enableScissor(x, y + HEADER, x + width, y + height);
			RenderUtil.withAlpha(e, () -> super.render(g, p, mouseX, mouseY, delta));
			g.disableScissor();
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (my < y + HEADER) {
			if (button == 0 && overSwitch(mx, my)) {
				module.toggle();
			} else if (button == 0 || button == 1) {
				expanded = !expanded;
				if (!expanded) {
					focus(null);
				}
			}
			return true;
		}
		if (expanded) {
			super.mouseClicked(mx, my, button);
		}
		return true;
	}

	public Module module() {
		return module;
	}
}
