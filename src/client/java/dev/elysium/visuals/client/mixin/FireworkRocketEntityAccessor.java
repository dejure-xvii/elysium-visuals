package dev.elysium.visuals.client.mixin;

import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FireworkESP: how long the rocket has been flying (ticks; counted on the client too). */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
	@Accessor("life")
	int elysium$getLife();
}
