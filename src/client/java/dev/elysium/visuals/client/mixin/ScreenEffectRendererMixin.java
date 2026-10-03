package dev.elysium.visuals.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.impl.render.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: fire overlay and the totem pop animation. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void elysium$noFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (NoRender.hides("fire")) {
			ci.cancel();
		}
	}

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void elysium$noTotem(ItemStack itemStack, RandomSource random, CallbackInfo ci) {
		if (NoRender.hides("totem")) {
			ci.cancel();
		}
	}
}
