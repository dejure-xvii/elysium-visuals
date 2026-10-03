package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.mixin.AbstractContainerScreenAccessor;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * Hold Shift + left mouse button and sweep over slots: every item the cursor
 * passes is shift-clicked (moved to the other inventory), like Mouse Tweaks.
 */
public class ItemScroller extends Module {
	private Slot lastSlot;
	private boolean wasDown;
	/** Shift and the left button as seen through the screen's own events (input may not reach the OS state). */
	private boolean shiftKey;
	private boolean leftButton;

	public ItemScroller() {
		super("item_scroller", "ItemScroller", "Shift + ЛКМ и провести по слотам — быстрое перемещение", Category.UTILS);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				shiftKey = false;
				leftButton = false;
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> sweep(container));
				ScreenMouseEvents.beforeMouseClick(screen).register((s, event) -> leftButton |= event.button() == 0);
				ScreenMouseEvents.beforeMouseRelease(screen).register((s, event) -> leftButton &= event.button() != 0);
				ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> trackShift(event.key(), true));
				ScreenKeyboardEvents.afterKeyRelease(screen).register((s, event) -> trackShift(event.key(), false));
			}
		});
	}

	private void trackShift(int key, boolean down) {
		if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			shiftKey = down;
		}
	}

	private void sweep(AbstractContainerScreen<?> screen) {
		Minecraft mc = Minecraft.getInstance();
		boolean down = mc.mouseHandler.isLeftPressed() || leftButton;
		Slot hovered = ((AbstractContainerScreenAccessor) screen).elysium$getHoveredSlot();
		boolean shift = mc.hasShiftDown() || shiftKey;
		if (!isEnabled() || !down || !shift || mc.player == null || mc.gameMode == null) {
			wasDown = down;
			lastSlot = null;
			return;
		}
		if (!wasDown) {
			// The press itself: vanilla already shift-clicked this slot.
			wasDown = true;
			lastSlot = hovered;
			return;
		}
		if (hovered != null && hovered != lastSlot) {
			lastSlot = hovered;
			if (hovered.hasItem() && hovered.mayPickup(mc.player)) {
				mc.gameMode.handleContainerInput(screen.getMenu().containerId, hovered.index, 0, ContainerInput.QUICK_MOVE, mc.player);
			}
		}
	}
}
