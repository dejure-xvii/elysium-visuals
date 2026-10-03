package dev.elysium.visuals.client.gui.tab;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.TabIcon;
import dev.elysium.visuals.client.gui.widget.ColorPicker;
import dev.elysium.visuals.client.gui.widget.ColorPreview;
import dev.elysium.visuals.client.gui.widget.ColorSlotRow;
import dev.elysium.visuals.client.gui.widget.HexField;
import dev.elysium.visuals.client.gui.widget.ThemeCard;
import dev.elysium.visuals.client.gui.widget.ThemedButton;
import dev.elysium.visuals.client.gui.widget.ToggleSwitch;
import dev.elysium.visuals.client.theme.ColorSlot;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.Theme;
import dev.elysium.visuals.client.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** "Темы": preset list plus the custom theme editor. */
public class ThemesTab extends Tab {
	private static final int CARD_H = 52;
	private static final int ROW_H = 18;
	private static final int ROW_GAP = 4;

	/** Remembered across GUI openings. */
	private static ColorSlot editing = ColorSlot.ACCENT;

	private static final int MIN_ROWS_W = 150;
	private static final int MIN_SQUARE = 56;

	private int customHeaderY;
	private int editorX;
	private int editorW;

	@Override
	public String title() {
		return "Темы";
	}

	@Override
	public TabIcon icon() {
		return TabIcon.THEMES;
	}

	@Override
	public String subtitle() {
		return "Готовые темы и своя";
	}

	@Override
	protected int build(int x, int y, int width) {
		ThemeManager themes = ThemeManager.get();
		editorX = x;
		editorW = width;
		// Slider handles stick out 2px past the picker; keep them inside the view.
		int usable = width - 4;

		// Theme cards: all in a row, or three per row when narrow.
		List<Theme> all = themes.allThemes();
		int gap = 6;
		int cols = width >= 300 ? all.size() : 3;
		int cardY = y + 2;
		int cardW = (width - gap * (cols - 1)) / cols;
		for (int i = 0; i < all.size(); i++) {
			ThemeCard card = add(new ThemeCard(all.get(i)));
			card.setBounds(x + (i % cols) * (cardW + gap), cardY + (i / cols) * (CARD_H + gap), cardW, CARD_H);
		}
		int rows = (all.size() + cols - 1) / cols;

		// Custom theme editor: rows on the left and the picker on the right,
		// or the picker below the rows when there isn't enough width.
		customHeaderY = cardY + rows * (CARD_H + gap) + 8;
		int top = customHeaderY + 15;
		int square = 86;
		int leftW = usable - ColorPicker.widthFor(square) - 10;
		if (leftW < MIN_ROWS_W) {
			square = Math.max(MIN_SQUARE, usable - 10 - MIN_ROWS_W - ColorPicker.widthFor(0));
			leftW = usable - ColorPicker.widthFor(square) - 10;
		}
		boolean stacked = leftW < MIN_ROWS_W;
		if (stacked) {
			leftW = width;
			square = Math.max(MIN_SQUARE, Math.min(86, usable - ColorPicker.widthFor(0)));
		}
		int pickerW = ColorPicker.widthFor(square);

		int rowY = top;
		for (ColorSlot slot : ColorSlot.values()) {
			ColorSlotRow row = add(new ColorSlotRow(slot, () -> editing == slot, s -> editing = s));
			row.setBounds(x, rowY, leftW, ROW_H);
			rowY += ROW_H + ROW_GAP;
		}

		ToggleSwitch glass = add(new ToggleSwitch("Стиль Liquid Glass",
				() -> themes.custom().glass(), themes::setCustomGlass));
		glass.setBounds(x, rowY, leftW, ROW_H);
		rowY += ROW_H + ROW_GAP + 2;

		ThemedButton copy = add(new ThemedButton("Взять цвета текущей темы",
				() -> themes.copyToCustom(themes.active())));
		copy.setBounds(x, rowY, leftW, 16);
		rowY += 16;

		int pickerX = stacked ? x : x + leftW + 10;
		if (stacked) {
			top = rowY + 10;
		}

		ColorPicker picker = add(new ColorPicker(
				() -> themes.custom().color(editing),
				c -> themes.setCustomColor(editing, c)));
		picker.setBounds(pickerX, top, pickerW, square);

		int fieldY = top + square + 6;
		int previewW = 28;
		// The HEX text needs ~70px; widen the field past the picker if the picker is small.
		int fieldW = Math.max(pickerW - previewW - 4, Math.min(76, usable - previewW - 4 - (pickerX - x)));
		HexField hex = add(new HexField(
				() -> themes.custom().color(editing),
				c -> themes.setCustomColor(editing, c)));
		hex.setBounds(pickerX, fieldY, fieldW, 16);

		ColorPreview preview = add(new ColorPreview(() -> themes.custom().color(editing)));
		preview.setBounds(pickerX + fieldW + 4, fieldY, previewW, 16);

		int bottom = Math.max(rowY, fieldY + 16);
		return bottom - y + 6;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		String custom = "Своя тема";
		RenderUtil.textBold(g, custom, editorX, customHeaderY, p.text());
		boolean active = ThemeManager.get().isCustomActive();
		String hint = active ? "активна" : "изменения включат её";
		int hintX = editorX + RenderUtil.widthBold(custom) + 8;
		if (active) {
			RenderUtil.glowDot(g, hintX + 1.5f, customHeaderY + 4, 3.5f, p.accent2());
			hintX += 7;
		}
		if (hintX + RenderUtil.captionWidth(hint) <= editorX + editorW) {
			RenderUtil.caption(g, hint, hintX, customHeaderY, active ? p.accent2() : p.textFaint());
		}
	}
}
