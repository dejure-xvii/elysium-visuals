package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.Ambience;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ambience time of day: the client's clocks report the chosen time (sky, light, stars follow it). */
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
	@Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
	private void elysium$time(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
		long ticks = cir.getReturnValueJ();
		long changed = Ambience.overrideClock(ticks);
		if (changed != ticks) {
			cir.setReturnValue(changed);
		}
	}
}
