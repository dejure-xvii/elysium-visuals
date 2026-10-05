package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.mixin.AbstractContainerScreenAccessor;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.notify.Notifications;
import dev.elysium.visuals.client.render.ThemeColors;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Items in the chosen slots can't be thrown away: neither with Q nor by
 * dragging them out of the inventory window. Locked slots get a small lock.
 */
public class LockSlot extends Module {
	private final MultiSelectSetting hotbar = add(new MultiSelectSetting("hotbar", "Слоты хотбара", hotbarOptions(), Set.of("1")));
	private final BooleanSetting more = add(new BooleanSetting("more", "Добавить ещё (инвентарь)", false));
	private final MultiSelectSetting inventory = add(new MultiSelectSetting("inventory", "Слоты инвентаря", inventoryOptions(), Set.of()))
			.visibleWhen(more::isOn);

	/** The stack on the cursor was picked up from a locked slot. */
	private boolean carryingLocked;
	private long lastNotice;

	public LockSlot() {
		super("lock_slot", "LockSlot", "Запрещает выбрасывать предметы из выбранных слотов", Category.UTILS);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				carryingLocked = false;
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> drawLocks(container, g));
			}
		});
	}

	private static List<MultiSelectSetting.Option> hotbarOptions() {
		List<MultiSelectSetting.Option> list = new ArrayList<>();
		for (int i = 1; i <= 9; i++) {
			list.add(option(String.valueOf(i), "Слот " + i));
		}
		return list;
	}

	private static List<MultiSelectSetting.Option> inventoryOptions() {
		List<MultiSelectSetting.Option> list = new ArrayList<>();
		for (int row = 1; row <= 3; row++) {
			for (int col = 1; col <= 9; col++) {
				list.add(option(String.valueOf(9 + (row - 1) * 9 + col - 1), "Ряд " + row + ", слот " + col));
			}
		}
		return list;
	}

	private static LockSlot active() {
		LockSlot m = ModuleManager.get().find(LockSlot.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** Player inventory index: 0-8 hotbar, 9-35 main inventory. */
	public boolean locked(int index) {
		if (index >= 0 && index < 9) {
			return hotbar.isSelected(String.valueOf(index + 1));
		}
		return more.isOn() && index >= 9 && index < 36 && inventory.isSelected(String.valueOf(index));
	}

	private boolean locked(Slot slot) {
		return slot != null && slot.container instanceof Inventory && locked(slot.getContainerSlot());
	}

	private void notice() {
		if (Util.getMillis() - lastNotice > 800) {
			lastNotice = Util.getMillis();
			Notifications.alert("Слот заблокирован", "Предмет из этого слота нельзя выбросить");
		}
	}

	/** Q in the world: true cancels the drop. */
	public static boolean blocksDrop(Player player) {
		LockSlot m = active();
		if (m == null || !m.locked(player.getInventory().getSelectedSlot())) {
			return false;
		}
		m.notice();
		return true;
	}

	/** A click in a container window: true cancels it (it would throw a locked item out). */
	public static boolean blocksClick(AbstractContainerMenu menu, int slotId, int button, ContainerInput input) {
		LockSlot m = active();
		if (m == null) {
			return false;
		}
		Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.getSlot(slotId) : null;
		if (input == ContainerInput.THROW && m.locked(slot)) {
			m.notice();
			return true;
		}
		if (slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && m.carryingLocked && !menu.getCarried().isEmpty()) {
			m.notice();
			return true;
		}
		if (input == ContainerInput.PICKUP && slot != null) {
			// Picking a stack up from a locked slot: remember it until it is put down.
			m.carryingLocked = menu.getCarried().isEmpty() && m.locked(slot) && slot.hasItem();
		} else if (slot != null && input != ContainerInput.QUICK_CRAFT) {
			m.carryingLocked = false;
		}
		return false;
	}

	private void drawLocks(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
		if (!isEnabled()) {
			return;
		}
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		for (Slot slot : screen.getMenu().slots) {
			if (locked(slot)) {
				lock(g, acc.elysium$leftPos() + slot.x + 16, acc.elysium$topPos() + slot.y);
			}
		}
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int x0 = g.guiWidth() / 2 - 91, y = g.guiHeight() - 22;
		for (int i = 0; i < 9; i++) {
			if (locked(i)) {
				lock(g, x0 + i * 20 + 19, y + 3);
			}
		}
	}

	/** A small padlock whose top-right corner is at (x, y). */
	private static void lock(GuiGraphicsExtractor g, float x, float y) {
		int c = ThemeColors.secondary() | 0xFF000000;
		float bx = x - 6, by = y + 2.5f;
		RenderUtil.softGlow(g, bx, by, 5, 4, 1, 0x80000000, 2);
		RenderUtil.roundedOutline(g, bx + 1, by - 2.5f, 3, 4, 1.5f, 0.9f, c);
		RenderUtil.roundedRect(g, bx, by, 5, 4, 1, c);
		RenderUtil.rect(g, bx + 2.1f, by + 1.2f, 0.8f, 1.5f, 0xFF000000);
	}
}
