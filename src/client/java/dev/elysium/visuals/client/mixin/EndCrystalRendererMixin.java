package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.render.CrystalTint;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.impl.render.CustomCrystal;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CustomCrystal: tints the crystal model by the danger of its blast. */
@Mixin(EndCrystalRenderer.class)
abstract class EndCrystalRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
			at = @At("TAIL"))
	private void elysium$tint(EndCrystal crystal, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
		((CrystalTint) state).elysium$setTint(CustomCrystal.tint(crystal));
	}

	@Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void elysium$submitTinted(SubmitNodeCollector collector, Model<Object> model, Object state, PoseStack ps, Identifier texture,
									  int light, int overlay, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		int tint = state instanceof CrystalTint t ? t.elysium$tint() : -1;
		if (tint == -1) {
			collector.submitModel(model, state, ps, texture, light, overlay, outline, crumbling);
		} else {
			collector.submitModel(model, state, ps, model.renderType(texture), light, overlay, tint, null, outline, crumbling);
		}
	}
}
