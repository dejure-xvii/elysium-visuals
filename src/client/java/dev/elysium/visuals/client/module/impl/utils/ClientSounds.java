package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Short chimes when modules are switched on and off. The sounds are our own
 * (assets/elysium-visuals/sounds/*.ogg, declared in sounds.json).
 */
public class ClientSounds extends Module {
	public static final SoundEvent MODULE_ON = SoundEvent.createVariableRangeEvent(ElysiumVisuals.id("module_on"));
	public static final SoundEvent MODULE_OFF = SoundEvent.createVariableRangeEvent(ElysiumVisuals.id("module_off"));
	public static final SoundEvent NOTIFY = SoundEvent.createVariableRangeEvent(ElysiumVisuals.id("notify"));

	private final NumberSetting volume = add(new NumberSetting("volume", "Громкость", 0.6, 0.05, 1, 0.05));
	private final NumberSetting pitch = add(new NumberSetting("pitch", "Высота звука", 1, 0.5, 2, 0.05));

	public ClientSounds() {
		super("client_sounds", "ClientSounds", "Приятные звуки при включении и выключении модулей", Category.UTILS);
		enableByDefault();
		ModuleManager.get().onToggle((m, on) -> {
			// Our own switch-off is silent (we're already off); our switch-on chimes.
			if (isEnabled()) {
				play(on ? MODULE_ON : MODULE_OFF);
			}
		});
	}

	public void play(SoundEvent sound) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch.floatValue(), volume.floatValue()));
	}
}
