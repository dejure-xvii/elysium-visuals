package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Optimizer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optimizer "Убрать блюр": no blur pass behind menus. */
@Mixin(Screen.class)
abstract class ScreenBlurMixin {
	@Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
	private void elysium$noBlur(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (Optimizer.active("blur")) {
			ci.cancel();
		}
	}
}
