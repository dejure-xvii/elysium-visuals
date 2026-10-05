package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.render.CrystalTint;

import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the CustomCrystal tint to the crystal render state. */
@Mixin(EndCrystalRenderState.class)
abstract class EndCrystalRenderStateMixin implements CrystalTint {
	@Unique
	private int elysium$tint = -1;

	@Override
	public int elysium$tint() {
		return elysium$tint;
	}

	@Override
	public void elysium$setTint(int tint) {
		elysium$tint = tint;
	}
}
