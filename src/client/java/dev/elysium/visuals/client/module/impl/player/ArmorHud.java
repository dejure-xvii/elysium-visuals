package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.Alerts;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Worn armor and held items with their durability; warns before something breaks. */
public class ArmorHud extends Module {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final BooleanSetting hands = add(new BooleanSetting("hands", "Предметы в руках", true));
	private final BooleanSetting percent = add(new BooleanSetting("percent", "Прочность в процентах", true));
	private final BooleanSetting horizontal = add(new BooleanSetting("horizontal", "Горизонтально", false));
	private final NumberSetting warnAt = add(new NumberSetting("warn_at", "Предупреждать при", 10, 1, 50, 1, "%"));
	private final BooleanSetting warn = add(new BooleanSetting("warn", "Звук и сообщение", true));

	/** Slots that already triggered the low-durability warning. */
	private final Set<EquipmentSlot> warned = new HashSet<>();

	public ArmorHud() {
		super("armor_hud", "Armor HUD", "Броня и предметы в руках с прочностью", Category.PLAYER);
		addHud(new Element());
	}

	private List<EquipmentSlot> slots() {
		List<EquipmentSlot> list = new ArrayList<>(List.of(ARMOR));
		if (hands.isOn()) {
			list.add(EquipmentSlot.MAINHAND);
			list.add(EquipmentSlot.OFFHAND);
		}
		return list;
	}

	private static float durability(ItemStack stack) {
		return stack.isDamageableItem() && stack.getMaxDamage() > 0
				? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : -1f;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		for (EquipmentSlot slot : slots()) {
			ItemStack stack = player.getItemBySlot(slot);
			float d = durability(stack);
			boolean low = d >= 0 && d * 100 <= warnAt.get();
			if (low && warned.add(slot) && warn.isOn()) {
				Alerts.ping(1.4f);
				Alerts.actionBar("Низкая прочность: " + stack.getHoverName().getString(), 0xFF6060);
			} else if (!low) {
				warned.remove(slot);
			}
		}
	}

	private String label(ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return stack.getCount() > 1 ? "x" + stack.getCount() : "";
		}
		if (percent.isOn()) {
			return Math.round(durability(stack) * 100) + "%";
		}
		return String.valueOf(stack.getMaxDamage() - stack.getDamageValue());
	}

	private final class Element extends HudElement {
		private static final int CELL = 18;

		private record Row(ItemStack stack, String text, float durability) {
		}

		private List<Row> rows = List.of();

		Element() {
			super("armor", "Броня", Anchor.BOTTOM_RIGHT);
		}

		private List<Row> collect(boolean preview) {
			LocalPlayer player = Minecraft.getInstance().player;
			List<Row> list = new ArrayList<>();
			if (player != null) {
				for (EquipmentSlot slot : slots()) {
					ItemStack stack = player.getItemBySlot(slot);
					if (!stack.isEmpty()) {
						list.add(new Row(stack, label(stack), durability(stack)));
					}
				}
			}
			if (list.isEmpty() && preview) {
				list.add(new Row(new ItemStack(Items.DIAMOND_HELMET), percent.isOn() ? "87%" : "317", 0.87f));
				list.add(new Row(new ItemStack(Items.DIAMOND_CHESTPLATE), percent.isOn() ? "9%" : "48", 0.09f));
			}
			return list;
		}

		@Override
		public boolean hasContent() {
			return !collect(false).isEmpty();
		}

		@Override
		protected void measure(boolean preview) {
			List<Row> next = collect(preview);
			if (!next.isEmpty()) {
				rows = next;
			}
			int textW = 0;
			for (Row r : rows) {
				textW = Math.max(textW, RenderUtil.width(r.text()));
			}
			int n = Math.max(1, rows.size());
			if (horizontal.isOn()) {
				int cellW = Math.max(CELL, textW + 2);
				width = HudStyle.PAD * 2 + n * cellW + (n - 1) * 3;
				height = HudStyle.PAD + 16 + 12 + 4;
			} else {
				width = HudStyle.PAD * 2 + 16 + 6 + Math.max(36, textW);
				height = HudStyle.PAD + n * CELL + 2;
			}
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			HudStyle.panel(g, p, x, y, width, height);
			float pulse = 0.55f + 0.45f * (float) Math.sin(Util.getMillis() / 120.0);
			int textW = 0;
			for (Row r : rows) {
				textW = Math.max(textW, RenderUtil.width(r.text()));
			}
			int cellW = Math.max(CELL, textW + 2);
			for (int i = 0; i < rows.size(); i++) {
				Row r = rows.get(i);
				boolean low = r.durability() >= 0 && r.durability() * 100 <= warnAt.get();
				int color = r.durability() < 0 ? p.text()
						: low ? ColorUtil.mulAlpha(0xFFFF6060, pulse) : ColorUtil.mix(0xFFFF6060, 0xFF7CFF8A, r.durability());
				int ix, iy;
				if (horizontal.isOn()) {
					ix = x + HudStyle.PAD + i * (cellW + 3) + (cellW - 16) / 2;
					iy = y + HudStyle.PAD - 1;
					RenderUtil.text(g, null, r.text(), ix + 8 - RenderUtil.width(r.text()) / 2, iy + 19, color);
				} else {
					ix = x + HudStyle.PAD;
					iy = y + HudStyle.PAD / 2 + 1 + i * CELL;
					int tx = ix + 22;
					RenderUtil.text(g, null, r.text(), tx, iy + 2, color);
					if (r.durability() >= 0) {
						HudStyle.bar(g, p, tx, iy + 12, width - HudStyle.PAD - tx, 2, r.durability(), color);
					}
				}
				if (RenderUtil.alpha() > 0.6f) {
					g.item(r.stack(), ix, iy);
				}
			}
		}
	}
}
