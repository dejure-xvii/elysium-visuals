package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Like the Crystal Optimizer mod: an end crystal you hit disappears on your
 * client right away, without waiting for the server, so the next one can be
 * placed sooner. Only the usual attack is sent; nothing extra.
 */
public class CrystalOptimizer extends Module {
	public CrystalOptimizer() {
		super("crystal_optimizer", "CrystalOptimizer", "Кристалл исчезает сразу после удара (быстрее ставить следующий)", Category.UTILS);
	}

	/** Called (mixin) after an attack was sent. */
	public static void onAttacked(Entity target) {
		CrystalOptimizer m = ModuleManager.get().find(CrystalOptimizer.class);
		Minecraft mc = Minecraft.getInstance();
		if (m != null && m.isEnabled() && target instanceof EndCrystal && mc.level != null && !target.isRemoved()) {
			mc.level.removeEntity(target.getId(), Entity.RemovalReason.KILLED);
		}
	}
}
