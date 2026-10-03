package dev.elysium.visuals.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.impl.render.Hands;
import dev.elysium.visuals.client.module.impl.render.NoRender;
import dev.elysium.visuals.client.render.WorldEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender hurt camera; post effects that run on the finished world image. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void elysium$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		// Keep the death tilt; only the shake on damage goes away.
		if (NoRender.hides("hurt_cam") && !cameraState.entityRenderState.isDeadOrDying) {
			ci.cancel();
		}
	}

	// Right after the world is drawn: the depth buffer still holds the world (it is
	// cleared for the hand next), and the hand isn't drawn yet, so it stays sharp.
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;render", shift = At.Shift.AFTER))
	private void elysium$afterWorld(DeltaTracker deltaTracker, CallbackInfo ci) {
		WorldEffects.afterWorld((GameRenderer) (Object) this);
	}

	// Right after the hand (and screen effects) are drawn: the depth buffer now marks the hand.
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V",
			shift = At.Shift.AFTER))
	private void elysium$afterHand(DeltaTracker deltaTracker, CallbackInfo ci) {
		Hands.process((GameRenderer) (Object) this);
	}
}
