package dev.elysium.visuals.test.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test harness only: Punchy registers some key mappings after Fabric's game test
 * runner has captured the default options, so restoring them passes a null key
 * name. Treat it as "unbound" instead of failing before any test runs.
 */
@Mixin(InputConstants.class)
public abstract class InputConstantsTestMixin {
	@Inject(method = "getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;", at = @At("HEAD"), cancellable = true)
	private static void elysiumTest$nullKey(String name, CallbackInfoReturnable<InputConstants.Key> cir) {
		if (name == null) {
			cir.setReturnValue(InputConstants.UNKNOWN);
		}
	}
}
