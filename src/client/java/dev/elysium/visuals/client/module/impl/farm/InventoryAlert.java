package dev.elysium.visuals.client.module.impl.farm;

import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.util.Alerts;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Free inventory slots on the HUD and a warning when the inventory is (almost) full. */
public class InventoryAlert extends Module {
	private static final int MAIN_SLOTS = 36;

	private final NumberSetting warnAt = add(new NumberSetting("warn_at", "Предупреждать при свободных слотах", 0, 0, 9, 1));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Звук", true));
	private final BooleanSetting message = add(new BooleanSetting("message", "Сообщение над хотбаром", true));
	private final BooleanSetting showSlots = add(new BooleanSetting("show_slots", "Свободные слоты на экране", true));

	private boolean warned;

	public InventoryAlert() {
		super("inventory_alert", "Inventory Full", "Предупреждает, когда инвентарь заполнен", Category.FARM);
		addHud(new LabelElement("slots", "Свободные слоты", LabelElement.Anchor.TOP_LEFT) {
			@Override
			public boolean hasContent() {
				return Minecraft.getInstance().player != null;
			}

			@Override
			protected List<Segment> segments(boolean preview) {
				LocalPlayer player = Minecraft.getInstance().player;
				int free = player != null ? freeSlots(player) : 12;
				int color = free <= warnAt.intValue()
						? ColorUtil.mulAlpha(0xFFFF6060, 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / 150.0))
						: free <= 5 ? 0xFFFFD27A : 0xFF7CFF8A;
				return List.of(new Segment("Инвентарь", 0, true), new Segment(free + " / " + MAIN_SLOTS, color, false));
			}
		}).visibleWhen(showSlots::isOn);
	}

	private static int freeSlots(LocalPlayer player) {
		Inventory inv = player.getInventory();
		int free = 0;
		for (int i = 0; i < MAIN_SLOTS; i++) {
			if (inv.getItem(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || player.isCreative()) {
			return;
		}
		int free = freeSlots(player);
		if (free <= warnAt.intValue()) {
			if (!warned) {
				if (sound.isOn()) {
					Alerts.ping(0.8f);
				}
				if (message.isOn()) {
					Alerts.actionBar(free == 0 ? "Инвентарь заполнен!" : "Свободных слотов: " + free, 0xFFD27A);
				}
			}
			warned = true;
		} else {
			warned = false;
		}
	}
}
