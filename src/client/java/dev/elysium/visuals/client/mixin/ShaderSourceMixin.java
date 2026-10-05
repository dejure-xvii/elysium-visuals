package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.render.ChunkAnimator;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.shaders.ShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ChunkAnimator under Sodium: Sodium's terrain vertex shader gets the slide-in offset. */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
abstract class ShaderSourceMixin {
	private static final Identifier SODIUM_TERRAIN = Identifier.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque");

	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
	private void elysium$patchSodiumTerrain(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		String source = cir.getReturnValue();
		if (source != null && type == ShaderType.VERTEX && SODIUM_TERRAIN.equals(id)) {
			cir.setReturnValue(ChunkAnimator.patchSodiumShader(source));
		}
	}
}
