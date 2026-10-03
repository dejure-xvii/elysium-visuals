package dev.elysium.visuals.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.impl.render.SwingAnimation;
import dev.elysium.visuals.client.module.impl.render.ViewModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ViewModel (hand position/size) and SwingAnimation (custom swing styles). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	private static HumanoidArm elysium$arm(AbstractClientPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void elysium$viewModelOffset(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
										 float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
										 SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ViewModel.translate(poseStack, elysium$arm(player, hand));
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void elysium$viewModelScale(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
										float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
										SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ViewModel.scale(poseStack, elysium$arm(player, hand));
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void elysium$swing(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		if (SwingAnimation.apply(poseStack, attack, invert, arm)) {
			ci.cancel();
		}
	}
}
