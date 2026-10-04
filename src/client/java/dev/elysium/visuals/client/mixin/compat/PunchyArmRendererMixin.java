package dev.elysium.visuals.client.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.impl.render.ViewModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While Punchy draws the first-person arms it skips the vanilla arm code
 * (where ViewModel normally hooks in), so ViewModel is applied around
 * Punchy's own first-person render instead.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer")
public abstract class PunchyArmRendererMixin {
	@Inject(method = "renderFirstPerson", at = @At("HEAD"))
	private static void elysium$viewModelPush(ItemInHandRenderer renderer, LocalPlayer player, float partialTicks,
											  PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ViewModel.pushForPunchy(poseStack, player);
	}

	@Inject(method = "renderFirstPerson", at = @At("RETURN"))
	private static void elysium$viewModelPop(ItemInHandRenderer renderer, LocalPlayer player, float partialTicks,
											 PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.popPose();
	}
}
