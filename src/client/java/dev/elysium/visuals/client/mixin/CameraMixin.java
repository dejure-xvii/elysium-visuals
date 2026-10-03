package dev.elysium.visuals.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.elysium.visuals.client.module.impl.render.AspectRatio;
import dev.elysium.visuals.client.module.impl.render.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NoRender camera clip; AspectRatio (world projection and the matching culling frustum). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	@Final
	private Projection projection;

	@Shadow
	private float fov;

	@Shadow
	private float depthFar;

	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void elysium$cameraClip(float cameraDist, CallbackInfoReturnable<Float> cir) {
		if (NoRender.hides("camera_clip")) {
			cir.setReturnValue(cameraDist);
		}
	}

	@Inject(method = "setupPerspective", at = @At("HEAD"), cancellable = true)
	private void elysium$aspect(float zNear, float zFar, float fovDeg, float width, float height, CallbackInfo ci) {
		Float ratio = AspectRatio.current();
		if (ratio != null) {
			projection.setupPerspective(zNear, zFar, fovDeg, height * ratio, height);
			ci.cancel();
		}
	}

	/** With a wider ratio more is visible at the sides: widen the culling frustum too, or edges would pop out. */
	@Inject(method = "createProjectionMatrixForCulling", at = @At("RETURN"), cancellable = true)
	private void elysium$aspectCulling(CallbackInfoReturnable<Matrix4f> cir) {
		Float ratio = AspectRatio.current();
		if (ratio == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float window = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		float cullFov = Math.max(fov, mc.options.fov().get().intValue());
		cir.setReturnValue(new Matrix4f().perspective(cullFov * (float) (Math.PI / 180.0), Math.max(window, ratio),
				0.05F, depthFar, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()));
	}
}
