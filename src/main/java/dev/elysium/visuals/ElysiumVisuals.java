package dev.elysium.visuals;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants. The mod is client-only, so there is no common entrypoint;
 * everything starts in {@code dev.elysium.visuals.client.ElysiumVisualsClient}.
 */
public final class ElysiumVisuals {
	public static final String MOD_ID = "elysium-visuals";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ElysiumVisuals() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
