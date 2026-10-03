package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * While you mine a block, holds the best tool from the hotbar; when you stop,
 * goes back to the slot you had. Tools about to break can be left alone.
 */
public class AutoTool extends Module {
	/** Ticks without mining before the old slot comes back (so short pauses don't flip-flop). */
	private static final int RETURN_DELAY = 4;

	private final BooleanSetting returnSlot = add(new BooleanSetting("return_slot", "Возвращать слот", true));
	private final BooleanSetting protect = add(new BooleanSetting("protect", "Не ломать инструменты", true));
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Порог прочности", 5, 1, 50, 1, "%"))
			.visibleWhen(protect::isOn);

	private int previousSlot = -1;
	private int idleTicks;

	public AutoTool() {
		super("auto_tool", "AutoTool", "Берёт лучший инструмент из хотбара, пока копаешь", Category.PLAYER);
	}

	@Override
	protected void onDisable() {
		restore(Minecraft.getInstance().player);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		HitResult hit = mc.hitResult;
		boolean mining = mc.options.keyAttack.isDown() && mc.gui.screen() == null
				&& hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
				&& !mc.level.getBlockState(bhr.getBlockPos()).isAir();
		if (!mining) {
			if (previousSlot >= 0 && ++idleTicks >= RETURN_DELAY) {
				restore(player);
			}
			return;
		}
		idleTicks = 0;
		BlockState state = mc.level.getBlockState(((BlockHitResult) hit).getBlockPos());
		Inventory inv = player.getInventory();
		int current = inv.getSelectedSlot();
		int best = current;
		float bestScore = score(inv.getItem(current), state);
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			float s = score(inv.getItem(i), state);
			if (s > bestScore) {
				bestScore = s;
				best = i;
			}
		}
		if (best != current) {
			if (previousSlot < 0) {
				previousSlot = current;
			}
			inv.setSelectedSlot(best);
		}
	}

	/** How good {@code stack} is for {@code state}: mining speed, a bonus if it gets drops, -1 if it's too worn. */
	private float score(ItemStack stack, BlockState state) {
		if (protect.isOn() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			float left = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
			if (left * 100 <= threshold.get()) {
				return -1;
			}
		}
		float speed = stack.getDestroySpeed(state);
		if (speed <= 1f) {
			return stack.isEmpty() ? 1f : 0.99f; // prefer an empty hand over a useless item
		}
		return speed + (stack.isCorrectToolForDrops(state) ? 100 : 0);
	}

	private void restore(LocalPlayer player) {
		if (previousSlot >= 0 && returnSlot.isOn() && player != null) {
			player.getInventory().setSelectedSlot(previousSlot);
		}
		previousSlot = -1;
		idleTicks = 0;
	}
}
