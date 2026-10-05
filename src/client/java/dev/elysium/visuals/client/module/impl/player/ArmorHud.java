package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.util.Alerts;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Worn armor in a short hotbar of 4 slots to the right of the vanilla one, at
 * the same height and with the same look (the vanilla hotbar sprite, so
 * resource packs apply). Fixed in place like the hotbar; empty slots show the
 * armor silhouette. Warns before a piece breaks.
 */
public class ArmorHud extends Module {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier[] EMPTY = {
			Identifier.withDefaultNamespace("container/slot/helmet"),
			Identifier.withDefaultNamespace("container/slot/chestplate"),
			Identifier.withDefaultNamespace("container/slot/leggings"),
			Identifier.withDefaultNamespace("container/slot/boots")};
	/** Width of the 4-slot bar: the left end and 4 slots of the hotbar sprite (81 px) plus its right edge (1 px). */
	private static final int WIDTH = 82;

	private final NumberSetting warnAt = add(new NumberSetting("warn_at", "Предупреждать при", 10, 1, 50, 1, "%"));
	private final BooleanSetting warn = add(new BooleanSetting("warn", "Звук и сообщение", true));

	/** Slots that already triggered the low-durability warning. */
	private final Set<EquipmentSlot> warned = EnumSet.noneOf(EquipmentSlot.class);

	public ArmorHud() {
		super("armor_hud", "Armor HUD", "Броня в 4 слотах справа от хотбара", Category.PLAYER);
	}

	private static float durability(ItemStack stack) {
		return stack.isDamageableItem() && stack.getMaxDamage() > 0
				? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : -1f;
	}

	private boolean low(ItemStack stack) {
		float d = durability(stack);
		return d >= 0 && d * 100 <= warnAt.get();
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = player.getItemBySlot(slot);
			boolean low = low(stack);
			if (low && warned.add(slot) && warn.isOn()) {
				Alerts.ping(1.4f);
				Alerts.actionBar("Низкая прочность: " + stack.getHoverName().getString(), 0xFF6060);
			} else if (!low) {
				warned.remove(slot);
			}
		}
	}

	/** Left edge of the bar: right of the hotbar, past the off-hand slot and attack indicator when they are there. */
	static int barX(Minecraft mc, LocalPlayer player, int guiWidth) {
		int x = guiWidth / 2 + 91 + 6;
		boolean offhandRight = player.getMainArm().getOpposite() == HumanoidArm.RIGHT && !player.getOffhandItem().isEmpty();
		if (offhandRight) {
			x += 29;
		}
		if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
			x += 22; // room kept for it, so the bar doesn't jump while it shows up
		}
		return x;
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		int x = barX(mc, player, g.guiWidth()), y = g.guiHeight() - 22;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, x, y, WIDTH - 1, 22);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 181, 0, x + WIDTH - 1, y, 1, 22);
		float pulse = 0.55f + 0.45f * (float) Math.sin(Util.getMillis() / 120.0);
		for (int i = 0; i < ARMOR.length; i++) {
			ItemStack stack = player.getItemBySlot(ARMOR[i]);
			int sx = x + 3 + i * 20, sy = y + 3;
			if (stack.isEmpty()) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY[i], sx, sy, 16, 16, 0.35f);
				continue;
			}
			g.item(stack, sx, sy);
			g.itemDecorations(mc.font, stack, sx, sy);
			if (low(stack)) {
				RenderUtil.roundedOutline(g, sx - 1, sy - 1, 18, 18, 2, 1f, ColorUtil.mulAlpha(0xFFFF5050, pulse));
			}
		}
	}
}
