package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/** How many totems, golden apples, pearls etc. are left in the inventory. */
public class ItemCounter extends Module {
	private record Tracked(String id, Item item) {
	}

	private static List<Tracked> tracked;

	/** Created lazily: items can't be referenced before the registries are loaded. */
	private static List<Tracked> tracked() {
		if (tracked == null) {
			tracked = List.of(
					new Tracked("totem", Items.TOTEM_OF_UNDYING),
					new Tracked("gapple", Items.GOLDEN_APPLE),
					new Tracked("egapple", Items.ENCHANTED_GOLDEN_APPLE),
					new Tracked("pearl", Items.ENDER_PEARL),
					new Tracked("xp", Items.EXPERIENCE_BOTTLE),
					new Tracked("arrow", Items.ARROW));
		}
		return tracked;
	}

	private final MultiSelectSetting items = add(new MultiSelectSetting("items", "Предметы",
			List.of(
					option("totem", "Тотемы"),
					option("gapple", "Золотые яблоки"),
					option("egapple", "Зачарованные яблоки"),
					option("pearl", "Эндер-жемчуг"),
					option("xp", "Пузырьки опыта"),
					option("arrow", "Стрелы")),
			Set.of("totem", "gapple", "pearl")));
	private final BooleanSetting hideZero = add(new BooleanSetting("hide_zero", "Скрывать нулевые", false));
	private final BooleanSetting vertical = add(new BooleanSetting("vertical", "Вертикально", false));
	private final BooleanSetting highlightEmpty = add(new BooleanSetting("highlight_empty", "Подсвечивать закончившиеся", true));

	public ItemCounter() {
		super("item_counter", "Item Counter", "Счётчик тотемов, яблок, жемчуга и других расходников", Category.PLAYER);
		addHud(new Element());
	}

	private static int count(LocalPlayer player, Item item) {
		Inventory inv = player.getInventory();
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() == item) {
				total += s.getCount();
			}
		}
		return total;
	}

	private final class Element extends HudElement {
		private static final int CELL = 20;

		private record Entry(ItemStack icon, int count) {
		}

		private List<Entry> entries = List.of();

		Element() {
			super("items", "Расходники", Anchor.TOP_LEFT);
		}

		private List<Entry> collect(boolean preview) {
			LocalPlayer player = Minecraft.getInstance().player;
			List<Entry> list = new ArrayList<>();
			for (Tracked t : tracked()) {
				if (!items.isSelected(t.id())) {
					continue;
				}
				int n = player != null ? count(player, t.item()) : (preview ? 3 : 0);
				if (n > 0 || !hideZero.isOn() || preview) {
					list.add(new Entry(new ItemStack(t.item()), n));
				}
			}
			return list;
		}

		@Override
		public boolean hasContent() {
			return Minecraft.getInstance().player != null && !collect(false).isEmpty();
		}

		@Override
		protected void measure(boolean preview) {
			List<Entry> next = collect(preview);
			if (!next.isEmpty()) {
				entries = next;
			}
			int n = Math.max(1, entries.size());
			if (vertical.isOn()) {
				int textW = 0;
				for (Entry e : entries) {
					textW = Math.max(textW, RenderUtil.widthBold(String.valueOf(e.count())));
				}
				width = HudStyle.PAD * 2 + 16 + 5 + Math.max(10, textW);
				height = HudStyle.PAD + n * CELL - 2;
			} else {
				width = HudStyle.PAD * 2 + n * CELL + (n - 1) * 2;
				height = HudStyle.PAD + 16 + 10 + 3;
			}
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			HudStyle.panel(g, p, x, y, width, height);
			for (int i = 0; i < entries.size(); i++) {
				Entry e = entries.get(i);
				String text = String.valueOf(e.count());
				int color = e.count() == 0 && highlightEmpty.isOn() ? 0xFFFF6060 : p.text();
				int ix, iy;
				if (vertical.isOn()) {
					ix = x + HudStyle.PAD;
					iy = y + HudStyle.PAD / 2 + 1 + i * CELL;
					RenderUtil.textBold(g, text, ix + 21, iy + 4, color);
				} else {
					ix = x + HudStyle.PAD + i * (CELL + 2) + 2;
					iy = y + HudStyle.PAD - 2;
					RenderUtil.textBold(g, text, ix + 8 - RenderUtil.widthBold(text) / 2, iy + 18, color);
				}
				if (RenderUtil.alpha() > 0.6f) {
					g.item(e.icon(), ix, iy);
				}
				if (e.count() == 0) {
					RenderUtil.roundedRect(g, ix, iy, 16, 16, 3, ColorUtil.mulAlpha(p.background(), 0.55f));
				}
			}
		}
	}
}
