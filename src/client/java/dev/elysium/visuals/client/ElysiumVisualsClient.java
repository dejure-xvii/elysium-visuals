package dev.elysium.visuals.client;

import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.command.CommandManager;
import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.gui.render.GuiPipelines;
import dev.elysium.visuals.client.hud.HudManager;
import dev.elysium.visuals.client.input.Keybinds;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.SwingAnimation;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.render.WorldEffects;
import dev.elysium.visuals.client.render.WorldPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ElysiumVisualsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GuiPipelines.init();
		ParticleRenderTypes.init();
		WorldPipelines.init();
		WorldEffects.init();
		ModuleManager.get().init();
		HudManager.get().init();
		ConfigManager.load();
		Keybinds.register();
		CommandManager.get().init();
		// Runs while SwingAnimation is off too: it decides whether Punchy animates the hands.
		ClientTickEvents.END_CLIENT_TICK.register(SwingAnimation::syncHands);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.saveIfDirty());
		ElysiumVisuals.LOGGER.info("Elysium Visuals initialised");
	}
}
