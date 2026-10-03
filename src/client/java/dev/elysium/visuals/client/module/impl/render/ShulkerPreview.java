package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Hold Ctrl over a shulker box in any inventory to see its contents: a 9×3
 * window tinted with the box's color, its name and the items with counts.
 */
public class ShulkerPreview extends Module {
	private static final int CELL = 18;
	private static final int PAD = 6;
	private static final int TITLE = 13;
	/** Undyed shulker purple. */
	private static final int DEFAULT_TINT = 0xFF8E6A9A;

	private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

	public ShulkerPreview() {
		super("shulker_preview", "ShulkerPreview", "Ctrl + наведение на шалкер — окошко с содержимым", Category.RENDER);
	}

	/** Called (mixin) instead of the vanilla tooltip; returns true if it drew the preview. */
	public static boolean render(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
		ShulkerPreview m = ModuleManager.get().find(ShulkerPreview.class);
		if (m == null || !m.isEnabled() || !Minecraft.getInstance().hasControlDown() || !isShulker(stack)) {
			return false;
		}
		m.draw(g, stack, mouseX, mouseY);
		return true;
	}

	private static boolean isShulker(ItemStack stack) {
		return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
	}

	private void draw(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
		ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		contents.copyInto(items);

		DyeColor dye = ((ShulkerBoxBlock) ((BlockItem) stack.getItem()).getBlock()).getColor();
		int tint = dye == null ? DEFAULT_TINT : ColorUtil.withAlpha(dye.getTextureDiffuseColor(), 0xFF);
		Palette p = ThemeManager.get().palette();
		int w = PAD * 2 + CELL * 9, h = PAD + TITLE + CELL * 3 + PAD;
		// Next to the cursor, kept inside the screen.
		int x = mouseX + 12, y = mouseY - 12;
		if (x + w > g.guiWidth() - 4) {
			x = mouseX - 12 - w;
		}
		x = Math.max(4, Math.min(x, g.guiWidth() - w - 4));
		y = Math.max(4, Math.min(y, g.guiHeight() - h - 4));

		g.nextStratum(); // above the slots, like a tooltip
		int bg = ColorUtil.withAlpha(ColorUtil.mixRgb(p.background(), tint, 0.45f), 0xF0);
		RenderUtil.softGlow(g, x, y, w, h, 7, ColorUtil.withAlpha(tint, 0x60), 8);
		RenderUtil.roundedRect(g, x, y, w, h, 7, bg);
		RenderUtil.roundedOutline(g, x, y, w, h, 7, 0, ColorUtil.withAlpha(ColorUtil.mixRgb(tint, 0xFFFFFFFF, 0.3f), 0xC0));
		String title = RenderUtil.ellipsize(stack.getHoverName().getString(), w - PAD * 2, true);
		RenderUtil.textBold(g, title, x + PAD, y + 5, ColorUtil.luminance(bg) > 0.6f ? 0xFF1A1A1A : 0xFFFFFFFF);
		int gx = x + PAD, gy = y + PAD + TITLE;
		for (int i = 0; i < 27; i++) {
			int cx = gx + (i % 9) * CELL, cy = gy + (i / 9) * CELL;
			RenderUtil.roundedRect(g, cx + 1, cy + 1, CELL - 2, CELL - 2, 3, 0x30000000);
			ItemStack item = items.get(i);
			if (!item.isEmpty()) {
				g.item(item, cx + 1, cy + 1);
				g.itemDecorations(Minecraft.getInstance().font, item, cx + 1, cy + 1);
			}
		}
	}
}
