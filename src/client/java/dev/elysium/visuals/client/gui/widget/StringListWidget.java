package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Editor for a {@link StringListSetting}: input field with an add button, and
 * the entries below, each with a × to remove it.
 */
public class StringListWidget extends UiContainer {
	private static final int TITLE_H = 16;
	private static final int FIELD_H = 16;
	private static final int ROW_H = 16;
	private static final int ROW_GAP = 3;

	private final StringListSetting setting;
	private final TextField field;
	private final ThemedButton addButton;
	private long errorUntil;

	public StringListWidget(StringListSetting setting) {
		this.setting = setting;
		this.field = add(new TextField(setting.placeholder(), 32,
				c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_'), this::submit));
		this.addButton = add(new ThemedButton("+", () -> submit(field.text())));
	}

	private void submit(String text) {
		if (setting.add(text)) {
			field.setText("");
		} else if (!text.isBlank()) {
			errorUntil = Util.getMillis() + 600;
		}
	}

	@Override
	public int preferredHeight() {
		int rows = Math.max(1, setting.get().size());
		return TITLE_H + FIELD_H + 6 + rows * (ROW_H + ROW_GAP) + 3;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		height = preferredHeight();
		RenderUtil.well(g, x, y, width, height, 5, p, 0f);
		String count = String.valueOf(setting.get().size());
		RenderUtil.text(g, setting.name(), RenderUtil.Face.REGULAR, x + 8, y + 4, p.text());
		RenderUtil.textBold(g, count, x + width - 8 - RenderUtil.widthBold(count), y + 4, p.accent2());

		int fy = y + TITLE_H;
		field.setBounds(x + 5, fy, width - 10 - FIELD_H - 4, FIELD_H);
		addButton.setBounds(x + width - 5 - FIELD_H, fy, FIELD_H, FIELD_H);
		super.render(g, p, mouseX, mouseY, delta);
		if (Util.getMillis() < errorUntil) {
			RenderUtil.roundedOutline(g, field.x(), field.y(), field.width(), field.height(), 5, 1f, Palette.DANGER);
		}

		List<String> entries = setting.get();
		int ry = fy + FIELD_H + 6;
		if (entries.isEmpty()) {
			RenderUtil.text(g, "Список пуст", RenderUtil.Face.REGULAR, x + 9, ry + 4, p.textFaint());
			return;
		}
		for (String entry : entries) {
			boolean hovered = mouseX >= x + 5 && mouseX < x + width - 5 && mouseY >= ry && mouseY < ry + ROW_H;
			RenderUtil.roundedRect(g, x + 5, ry, width - 10, ROW_H, 4, ColorUtil.withAlpha(p.text(), hovered ? 0x14 : 0x0A));
			RenderUtil.glowDot(g, x + 12, ry + ROW_H / 2f, 4, p.accent2());
			RenderUtil.text(g, RenderUtil.ellipsize(entry, width - 40, false), RenderUtil.Face.REGULAR, x + 19, ry + 4, p.text());
			// Remove button "×".
			float cx = x + width - 13, cy = ry + ROW_H / 2f;
			boolean onRemove = hovered && mouseX >= cx - 6;
			RenderUtil.cross(g, cx, cy, 2.2f, onRemove ? Palette.DANGER : p.textFaint());
			ry += ROW_H + ROW_GAP;
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		int ry = y + TITLE_H + FIELD_H + 6;
		for (String entry : setting.get()) {
			if (my >= ry && my < ry + ROW_H && mx >= x + width - 21 && mx < x + width - 5) {
				setting.remove(entry);
				return true;
			}
			ry += ROW_H + ROW_GAP;
		}
		return true;
	}
}
