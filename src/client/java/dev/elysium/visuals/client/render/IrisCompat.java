package dev.elysium.visuals.client.render;

import dev.elysium.visuals.ElysiumVisuals;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Whether an Iris shader pack is active (through Iris' public API, by
 * reflection so Iris stays optional). Effects that draw over the world switch
 * themselves off then: shader packs bring their own sky, clouds and post effects.
 */
public final class IrisCompat {
	private static Method instance;
	private static Method shaderPackInUse;
	private static boolean lookedUp;

	private IrisCompat() {
	}

	public static boolean shaderPackInUse() {
		if (!lookedUp) {
			lookedUp = true;
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				try {
					Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
					instance = api.getMethod("getInstance");
					shaderPackInUse = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException | LinkageError e) {
					ElysiumVisuals.LOGGER.warn("Iris API not found; assuming no shader pack", e);
				}
			}
		}
		if (shaderPackInUse == null) {
			return false;
		}
		try {
			return (boolean) shaderPackInUse.invoke(instance.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true; // can't tell: be safe
		}
	}
}
