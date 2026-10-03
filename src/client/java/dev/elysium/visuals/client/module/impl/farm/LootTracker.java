package dev.elysium.visuals.client.module.impl.farm;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Counts what you collect during a farming session (from inventory changes)
 * and shows the top items with an hourly rate. Changes made while a screen
 * is open (chests, crafting, moving items) are not counted.
 */
public class LootTracker extends Module {
	private final NumberSetting rows = add(new NumberSetting("rows", "Строк в списке", 5, 1, 10, 1));
	private final BooleanSetting perHour = add(new BooleanSetting("per_hour", "Скорость в час", true));
	private final BooleanSetting resetOnDisable = add(new BooleanSetting("reset_on_disable", "Сбрасывать при выключении", true));

	private final Map<Item, Integer> gained = new LinkedHashMap<>();
	private Map<Item, Integer> last;
	private long activeMs;

	public LootTracker() {
		super("loot_tracker", "Loot Tracker", "Считает добытые предметы и скорость фарма в час", Category.FARM);
		addHud(new Element());
	}

	private static Map<Item, Integer> snapshot(LocalPlayer player) {
		Map<Item, Integer> counts = new HashMap<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) {
				counts.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || player.isCreative()) {
			last = null;
			return;
		}
		Map<Item, Integer> now = snapshot(player);
		// Only count pickups during normal play; anything done in a screen just re-baselines.
		if (last != null && mc.gui.screen() == null) {
			for (Map.Entry<Item, Integer> e : now.entrySet()) {
				int diff = e.getValue() - last.getOrDefault(e.getKey(), 0);
				if (diff > 0) {
					gained.merge(e.getKey(), diff, Integer::sum);
				}
			}
		}
		last = now;
		activeMs += 50;
	}

	@Override
	protected void onEnable() {
		if (resetOnDisable.isOn()) {
			gained.clear();
			activeMs = 0;
		}
		last = null;
	}

	private List<Map.Entry<Item, Integer>> top() {
		List<Map.Entry<Item, Integer>> list = new ArrayList<>(gained.entrySet());
		list.sort((a, b) -> b.getValue() - a.getValue());
		return list.subList(0, Math.min(rows.intValue(), list.size()));
	}

	private String rate(int count) {
		double hours = Math.max(activeMs, 60_000) / 3_600_000.0;
		double r = count / hours;
		return r >= 1000 ? String.format(Locale.ROOT, "%.1fk/ч", r / 1000) : Math.round(r) + "/ч";
	}

	private final class Element extends HudElement {
		private static final int ROW_H = 18;

		private record Row(ItemStack icon, String name, String count, String rate) {
		}

		private List<Row> list = List.of();
		private String total = "";

		Element() {
			super("loot", "Добыча", Anchor.TOP_LEFT);
		}

		@Override
		public boolean hasContent() {
			return !gained.isEmpty();
		}

		@Override
		protected void measure(boolean preview) {
			List<Row> next = new ArrayList<>();
			int sum = 0;
			for (Map.Entry<Item, Integer> e : top()) {
				ItemStack icon = new ItemStack(e.getKey());
				next.add(new Row(icon, icon.getHoverName().getString(), "+" + e.getValue(), perHour.isOn() ? rate(e.getValue()) : ""));
			}
			for (int v : gained.values()) {
				sum += v;
			}
			if (next.isEmpty() && preview) {
				next.add(new Row(new ItemStack(Items.WHEAT), "Пшеница", "+128", perHour.isOn() ? "960/ч" : ""));
				next.add(new Row(new ItemStack(Items.CARROT), "Морковь", "+64", perHour.isOn() ? "480/ч" : ""));
				sum = 192;
			}
			if (!next.isEmpty()) {
				list = next;
				total = sum + " шт.";
			}
			int w = Math.max(130, HudStyle.titleWidth("Добыча", total));
			for (Row r : list) {
				w = Math.max(w, HudStyle.PAD * 2 + 20 + RenderUtil.width(r.name()) + 10
						+ RenderUtil.widthBold(r.count()) + (r.rate().isEmpty() ? 0 : 6 + RenderUtil.width(r.rate())));
			}
			width = w;
			height = HudStyle.TITLE_H + 3 + Math.max(1, list.size()) * ROW_H + 2;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			HudStyle.panel(g, p, x, y, width, height);
			int ry = HudStyle.title(g, p, "Добыча", total, x, y, width);
			for (Row r : list) {
				if (RenderUtil.alpha() > 0.6f) {
					g.item(r.icon(), x + HudStyle.PAD, ry);
				}
				int right = x + width - HudStyle.PAD;
				if (!r.rate().isEmpty()) {
					right -= RenderUtil.width(r.rate());
					RenderUtil.text(g, null, r.rate(), right, ry + 4, p.textDim());
					right -= 6;
				}
				right -= RenderUtil.widthBold(r.count());
				RenderUtil.textBold(g, r.count(), right, ry + 4, p.accent());
				int tx = x + HudStyle.PAD + 20;
				RenderUtil.text(g, null, RenderUtil.ellipsize(r.name(), right - tx - 6, false), tx, ry + 4, p.text());
				ry += ROW_H;
			}
		}
	}
}
