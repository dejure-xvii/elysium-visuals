package dev.elysium.visuals.client.hud;

import dev.elysium.visuals.client.hud.style.HudData;
import dev.elysium.visuals.client.hud.style.InterfaceStyle;
import dev.elysium.visuals.client.hud.style.Skin;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * An Interface block made of rows (binds, buffs, cooldowns, friends). The
 * subclass collects the rows; the selected style draws them. Rows fade and
 * open/close when they appear or leave; an empty block is hidden, or kept as
 * its header in styles that prefer that.
 */
public abstract class ListElement extends HudElement {
	private final AnimatedRows<HudData.Row> rows = new AnimatedRows<>(HudData.Row::key);
	private List<HudData.Row> current = List.of();

	protected ListElement(String id, String name, Anchor anchor) {
		super(id, name, anchor);
	}

	protected abstract HudData.Block block();

	/** This frame's rows, in display order. */
	protected abstract List<HudData.Row> collect();

	/** Example rows for the HUD editor when there is no real data. */
	protected abstract List<HudData.Row> sample();

	@Override
	public boolean hasContent() {
		// Called once per frame before measure(); the rows are reused there.
		current = collect();
		return !current.isEmpty() || InterfaceStyle.current().keepsEmptyHeader();
	}

	@Override
	protected void measure(boolean preview) {
		Skin skin = InterfaceStyle.current();
		if (preview && current.isEmpty()) {
			rows.update(sample());
		} else if (!current.isEmpty() || skin.keepsEmptyHeader()) {
			rows.update(current);
		}
		// Otherwise the block is fading out: keep its last rows until it is gone.
		Skin.Size size = skin.measureList(block(), rows.rows());
		width = animateWidth(size.width());
		height = animateHeight(size.height());
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		InterfaceStyle.current().drawList(g, p, block(), rows.rows(), x, y, width, height);
	}
}
