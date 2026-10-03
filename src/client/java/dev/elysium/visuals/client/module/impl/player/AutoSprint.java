package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

/** Test module: holds the sprint key for you. */
public class AutoSprint extends Module {
	private final BooleanSetting onlyForward = add(new BooleanSetting("only_forward", "Только при движении вперёд", true));

	public AutoSprint() {
		super("auto_sprint", "Auto Sprint", "Автоматически включает бег", Category.PLAYER);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		boolean sprint = !onlyForward.isOn() || mc.options.keyUp.isDown();
		mc.options.keySprint.setDown(sprint);
	}

	@Override
	protected void onDisable() {
		Minecraft.getInstance().options.keySprint.setDown(false);
	}
}
