package dev.elysium.visuals.client.module.impl.farm;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.Alerts;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Warns before the tool in your hand breaks (only a warning: it never
 * switches items or blocks actions by itself).
 */
public class ToolGuard extends Module {
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Порог прочности", 20, 1, 200, 1));
	private final BooleanSetting percentMode = add(new BooleanSetting("percent_mode", "Порог в процентах", false));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Звук", true));
	private final NumberSetting repeat = add(new NumberSetting("repeat", "Повторять звук каждые", 10, 0, 60, 5, " с"));

	private ItemStack warnedFor = ItemStack.EMPTY;
	private long lastSoundMs;

	public ToolGuard() {
		super("tool_guard", "Tool Guard", "Предупреждает, что инструмент в руке скоро сломается", Category.FARM);
		addHud(new Element());
	}

	private boolean isLow(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return false;
		}
		int left = stack.getMaxDamage() - stack.getDamageValue();
		return percentMode.isOn()
				? left * 100.0 / stack.getMaxDamage() <= Math.min(100, threshold.get())
				: left <= threshold.intValue();
	}

	private ItemStack lowTool() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative()) {
			return ItemStack.EMPTY;
		}
		ItemStack main = player.getMainHandItem();
		return isLow(main) ? main : ItemStack.EMPTY;
	}

	@Override
	public void onTick(Minecraft mc) {
		ItemStack low = lowTool();
		if (low.isEmpty()) {
			warnedFor = ItemStack.EMPTY;
			return;
		}
		long now = Util.getMillis();
		boolean newTool = warnedFor.isEmpty() || warnedFor.getItem() != low.getItem();
		boolean again = repeat.intValue() > 0 && now - lastSoundMs > repeat.intValue() * 1000L;
		if (sound.isOn() && (newTool || again)) {
			Alerts.ping(1.8f);
			lastSoundMs = now;
		}
		warnedFor = low;
	}

	private final class Element extends HudElement {
		private ItemStack stack = ItemStack.EMPTY;
		private String text = "";

		Element() {
			super("tool", "Прочность инструмента", Anchor.CROSSHAIR);
		}

		@Override
		public boolean hasContent() {
			return !lowTool().isEmpty();
		}

		@Override
		protected void measure(boolean preview) {
			ItemStack low = lowTool();
			if (!low.isEmpty()) {
				stack = low;
			} else if (preview) {
				stack = new ItemStack(Items.DIAMOND_PICKAXE);
			}
			int left = stack.isEmpty() ? 0 : stack.getMaxDamage() - stack.getDamageValue();
			text = low.isEmpty() && preview ? "Осталось 12" : "Осталось " + left;
			width = HudStyle.PAD * 2 + 16 + 5 + RenderUtil.widthBold(text);
			height = 22;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			float pulse = 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / 140.0);
			HudStyle.panel(g, p, x, y, width, height);
			RenderUtil.roundedOutline(g, x, y, width, height, HudStyle.RADIUS, 1f, ColorUtil.mulAlpha(0xFFFF6060, pulse));
			if (RenderUtil.alpha() > 0.6f && !stack.isEmpty()) {
				g.item(stack, x + HudStyle.PAD, y + 3);
			}
			RenderUtil.textBold(g, text, x + HudStyle.PAD + 21, y + 7, ColorUtil.mix(p.text(), 0xFFFF6060, pulse));
		}
	}
}
