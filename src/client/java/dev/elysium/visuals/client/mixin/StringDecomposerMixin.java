package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.utils.NameProtect;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NameProtect: every string the game draws or measures passes through these
 * methods, so replacing names here covers chat, tab list, name tags, the
 * scoreboard and our HUD alike (and widths stay consistent).
 */
@Mixin(StringDecomposer.class)
public abstract class StringDecomposerMixin {
	@Unique
	private static final ThreadLocal<Boolean> ELYSIUM$INSIDE = ThreadLocal.withInitial(() -> false);

	@Inject(method = "iterate", at = @At("HEAD"), cancellable = true)
	private static void elysium$iterate(String string, Style style, FormattedCharSink output, CallbackInfoReturnable<Boolean> cir) {
		String replaced = elysium$replace(string, style);
		if (replaced != null) {
			cir.setReturnValue(elysium$call(() -> StringDecomposer.iterate(replaced, style, output)));
		}
	}

	@Inject(method = "iterateBackwards", at = @At("HEAD"), cancellable = true)
	private static void elysium$iterateBackwards(String string, Style style, FormattedCharSink output, CallbackInfoReturnable<Boolean> cir) {
		String replaced = elysium$replace(string, style);
		if (replaced != null) {
			cir.setReturnValue(elysium$call(() -> StringDecomposer.iterateBackwards(replaced, style, output)));
		}
	}

	@Inject(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			at = @At("HEAD"), cancellable = true)
	private static void elysium$iterateFormatted(String string, int offset, Style currentStyle, Style resetStyle, FormattedCharSink output,
												 CallbackInfoReturnable<Boolean> cir) {
		if (offset != 0) {
			return; // continuation of a string we already handled
		}
		String replaced = elysium$replace(string, currentStyle);
		if (replaced != null) {
			cir.setReturnValue(elysium$call(() -> StringDecomposer.iterateFormatted(replaced, 0, currentStyle, resetStyle, output)));
		}
	}

	/** The replaced string, or null when nothing changes (or we're already inside a replaced call). */
	@Unique
	private static String elysium$replace(String string, Style style) {
		if (string == null || ELYSIUM$INSIDE.get()) {
			return null;
		}
		String replaced = NameProtect.protect(string, style);
		return replaced == string ? null : replaced;
	}

	@Unique
	private static boolean elysium$call(java.util.function.BooleanSupplier body) {
		ELYSIUM$INSIDE.set(true);
		try {
			return body.getAsBoolean();
		} finally {
			ELYSIUM$INSIDE.set(false);
		}
	}
}
