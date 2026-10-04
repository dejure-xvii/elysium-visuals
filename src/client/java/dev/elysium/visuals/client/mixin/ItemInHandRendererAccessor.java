package dev.elysium.visuals.client.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Equip heights of the first-person hands (0 = lowered, 1 = fully raised). */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {
	@Accessor("mainHandHeight")
	void elysium$setMainHandHeight(float value);

	@Accessor("oMainHandHeight")
	void elysium$setOMainHandHeight(float value);

	@Accessor("offHandHeight")
	void elysium$setOffHandHeight(float value);

	@Accessor("oOffHandHeight")
	void elysium$setOOffHandHeight(float value);
}
