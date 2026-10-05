package dev.elysium.visuals.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.impl.render.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import dev.elysium.visuals.client.module.impl.render.NoCameraClip;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: fire overlay and the totem pop animation. NoCameraClip: no block texture with the head in a block. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void elysium$noFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (NoRender.hides("fire")) {
			ci.cancel();
		}
	}

	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
	private static void elysium$noInWall(Player player, CallbackInfoReturnable<BlockState> cir) {
		if (NoCameraClip.firstPersonThroughBlocks()) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void elysium$noTotem(ItemStack itemStack, RandomSource random, CallbackInfo ci) {
		if (NoRender.hides("totem")) {
			ci.cancel();
		}
	}
}
