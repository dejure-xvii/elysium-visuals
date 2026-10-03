package dev.elysium.visuals.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the slot under the mouse and the GUI position (ItemScroller, ShulkerPreview, tests). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("hoveredSlot")
	Slot elysium$getHoveredSlot();

	@Accessor("leftPos")
	int elysium$leftPos();

	@Accessor("topPos")
	int elysium$topPos();
}
