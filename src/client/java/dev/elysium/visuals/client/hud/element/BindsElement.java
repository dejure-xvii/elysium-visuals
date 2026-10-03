package dev.elysium.visuals.client.hud.element;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Modules that have a key bind, with the key; a green dot marks the enabled ones. */
public class BindsElement extends HudElement {
	private static final int ROW_H = 13;
	private static final String CAPTION = "Бинды";

	private record Row(String name, String key, boolean enabled) {
	}

	private final AnimatedRows<Row> rows = new AnimatedRows<>(Row::name);
	private String right = "";

	public BindsElement() {
		super("binds", "Привязанные модули", Anchor.TOP_LEFT);
	}

	private static List<Row> collect() {
		List<Row> result = new ArrayList<>();
		for (Module m : ModuleManager.get().modules()) {
			if (m.bind() != Module.NO_KEY) {
				String key = InputConstants.Type.KEYSYM.getOrCreate(m.bind()).getDisplayName().getString();
				result.add(new Row(m.name(), key, m.isEnabled()));
			}
		}
		return result;
	}

	@Override
	public boolean hasContent() {
		for (Module m : ModuleManager.get().modules()) {
			if (m.bind() != Module.NO_KEY) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void measure(boolean preview) {
		List<Row> current = collect();
		// While the element fades out, keep the last rows.
		if (!current.isEmpty() || preview) {
			rows.update(current);
			long on = current.stream().filter(Row::enabled).count();
			right = current.isEmpty() ? "" : on + " / " + current.size();
		}
		int w = Math.max(104, HudStyle.headerWidth(CAPTION, right));
		for (AnimatedRows.Row<Row> r : rows.rows()) {
			Row row = r.value();
			w = Math.max(w, HudStyle.PAD * 2 + 10 + RenderUtil.width(row.name()) + 12 + RenderUtil.width(row.key()) + 8);
		}
		width = animateWidth(w);
		height = HudStyle.TITLE_H + 3 + Math.round(Math.max(ROW_H, rows.height(ROW_H))) + 4;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		HudStyle.panel(g, p, x, y, width, height);
		int top = HudStyle.header(g, p, HudIcon.KEYBOARD, CAPTION, right, x, y, width);
		float empty = 1f - Math.min(1f, rows.height(ROW_H) / ROW_H);
		if (empty > 0.01f) {
			RenderUtil.withAlpha(empty, () -> RenderUtil.text(g, null, "Нет биндов", x + HudStyle.PAD, top + 3, p.textDim()));
		}
		float ry = top;
		for (AnimatedRows.Row<Row> r : rows.rows()) {
			Row row = r.value();
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> {
				int nameX = x + HudStyle.PAD + 9;
				HudStyle.dot(g, x + HudStyle.PAD + 2.5f, rowY + 6.5f, row.enabled() ? Palette.OK : ColorUtil.mulAlpha(p.textFaint(), 0.7f));
				RenderUtil.text(g, null, row.name(), nameX, rowY + 3, row.enabled() ? p.text() : p.textDim());
				int kw = RenderUtil.width(row.key()) + 8;
				int kx = x + width - HudStyle.PAD - kw;
				RenderUtil.roundedRect(g, kx, rowY + 1, kw, ROW_H - 2, 3.5f,
						row.enabled() ? p.accentSoft() : ColorUtil.withAlpha(p.text(), 0x12));
				RenderUtil.roundedOutline(g, kx, rowY + 1, kw, ROW_H - 2, 3.5f, 0,
						row.enabled() ? ColorUtil.mulAlpha(p.accent2(), 0.35f) : p.border());
				RenderUtil.text(g, null, row.key(), kx + 4, rowY + 3, row.enabled() ? p.accent2() : p.textDim());
			});
			ry += ROW_H * r.open();
		}
	}
}
