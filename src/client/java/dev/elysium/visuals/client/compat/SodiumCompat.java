package dev.elysium.visuals.client.compat;

import dev.elysium.visuals.ElysiumVisuals;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Sodium, optional: whether it is installed, and dropping its cached terrain
 * programs so they are built again from (our patched) shader sources. Done by
 * reflection, so a Sodium update that renames things only turns the feature off.
 */
public final class SodiumCompat {
	public static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sodium");
	private static final String RENDERER = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer";

	private static Map<?, ?> programs;
	private static boolean lookedUp;

	private SodiumCompat() {
	}

	/** Sodium builds the terrain programs again on the next frame. */
	public static void rebuildTerrainShaders() {
		if (!INSTALLED) {
			return;
		}
		if (!lookedUp) {
			lookedUp = true;
			try {
				Field f = Class.forName(RENDERER).getDeclaredField("programs");
				f.setAccessible(true);
				programs = (Map<?, ?>) f.get(null);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				ElysiumVisuals.LOGGER.warn("Sodium terrain programs not found; ChunkAnimator changes apply after F3+T", e);
			}
		}
		if (programs != null) {
			programs.clear();
		}
	}
}
