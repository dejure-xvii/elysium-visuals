package dev.elysium.visuals.client.mixin.vanilla;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** When a chunk section's mesh was first uploaded (0 until then); ChunkAnimator times the slide-in from it. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public interface RenderSectionAccessor {
	@Accessor("uploadedTime")
	long elysium$uploadedTime();
}
