package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.ShulkerPreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ShulkerPreview: shows the contents window instead of the vanilla tooltip. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected @Nullable Slot hoveredSlot;

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void elysium$shulkerPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		Slot slot = hoveredSlot;
		if (slot != null && slot.hasItem() && ShulkerPreview.render(graphics, slot.getItem(), mouseX, mouseY)) {
			ci.cancel();
		}
	}
}
