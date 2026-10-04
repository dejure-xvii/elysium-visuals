package dev.elysium.visuals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.elysium.visuals.client.menu.MenuBackgrounds;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws the selected menu panorama instead of the vanilla one (also behind the vanilla menu screens). */
@Mixin(GuiRenderer.class)
abstract class GuiRendererPanoramaMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V"))
	private void elysium$menuPanorama(CubeMap vanilla, float pitch, float spin, Operation<Void> original) {
		CubeMap own = MenuBackgrounds.activeCube();
		original.call(own != null ? own : vanilla, pitch, spin);
	}
}
