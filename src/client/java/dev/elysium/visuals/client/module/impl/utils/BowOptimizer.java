package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BowItem;

/**
 * Lets go of a drawn bow after the chosen number of ticks, exactly as letting
 * go of the use key does (the same {@code releaseUsingItem} call the game makes);
 * no packets of its own. Holding the key draws the next arrow as usual.
 */
public class BowOptimizer extends Module {
	private final NumberSetting ticks = add(new NumberSetting("ticks", "Скорость (тиков натяжения)", 6, 3, 20, 1, " т."));

	public BowOptimizer() {
		super("bow_optimizer", "BowOptimizer", "Отпускает лук через заданное число тиков натяжения", Category.UTILS);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || mc.gui.screen() != null || !player.isUsingItem()
				|| !(player.getUseItem().getItem() instanceof BowItem)) {
			return;
		}
		if (player.getTicksUsingItem() >= ticks.intValue()) {
			mc.gameMode.releaseUsingItem(player);
		}
	}
}
