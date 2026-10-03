package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item cooldowns (ender pearl, shield, chorus fruit, …) with the item icon,
 * a progress bar and the remaining time.
 *
 * <p>The game only exposes the cooldown as a fraction, so the total length is
 * estimated from how fast that fraction drops (and a known default until then).
 */
public class CooldownsElement extends HudElement {
	private static final int W = 132;
	private static final int ROW_H = 20;

	/**
	 * Items checked even when they aren't in the hotbar, with their vanilla
	 * cooldown in ms. Created lazily: item stacks can't exist before the game
	 * has finished loading its registries.
	 */
	private static Map<ItemStack, Integer> known;

	private static Map<ItemStack, Integer> known() {
		if (known == null) {
			known = new LinkedHashMap<>();
			known.put(new ItemStack(Items.ENDER_PEARL), 1000);
			known.put(new ItemStack(Items.CHORUS_FRUIT), 1000);
			known.put(new ItemStack(Items.WIND_CHARGE), 500);
			known.put(new ItemStack(Items.SHIELD), 5000);
			known.put(new ItemStack(Items.GOAT_HORN), 7000);
		}
		return known;
	}

	private record Row(String key, ItemStack stack, String name, float fraction, float seconds) {
	}

	/** Per cooldown group: when we first saw it and at which fraction. */
	private static final class Estimate {
		final long startMs;
		final float startFraction;
		final int defaultMs;
		float lastFraction;

		Estimate(float fraction, int defaultMs) {
			this.startMs = Util.getMillis();
			this.startFraction = fraction;
			this.lastFraction = fraction;
			this.defaultMs = defaultMs;
		}

		/** Remaining seconds, or -1 if unknown yet. */
		float remaining(float fraction) {
			long elapsed = Util.getMillis() - startMs;
			float dropped = startFraction - fraction;
			if (dropped > 0.02f && elapsed > 100) {
				return fraction * (elapsed / dropped) / 1000f;
			}
			if (defaultMs > 0 && startFraction > 0.9f) {
				return fraction * defaultMs / 1000f;
			}
			return -1;
		}
	}

	private static final String CAPTION = "Задержки";

	private final Map<Identifier, Estimate> estimates = new HashMap<>();
	private final AnimatedRows<Row> rows = new AnimatedRows<>(Row::key);
	private List<Row> current = List.of();

	public CooldownsElement() {
		super("cooldowns", "Задержки", Anchor.BOTTOM_RIGHT);
	}

	private List<Row> collect() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}
		ItemCooldowns cooldowns = player.getCooldowns();
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		Map<ItemStack, Integer> candidates = new LinkedHashMap<>();
		for (int i = 0; i < 9; i++) {
			candidates.put(player.getInventory().getItem(i), 0);
		}
		candidates.put(player.getOffhandItem(), 0);
		known().forEach(candidates::putIfAbsent);

		List<Row> result = new ArrayList<>();
		Map<Identifier, Boolean> seen = new HashMap<>();
		for (Map.Entry<ItemStack, Integer> c : candidates.entrySet()) {
			ItemStack stack = c.getKey();
			if (stack.isEmpty() || !cooldowns.isOnCooldown(stack)) {
				continue;
			}
			Identifier group = cooldowns.getCooldownGroup(stack);
			if (seen.put(group, true) != null) {
				continue;
			}
			float fraction = cooldowns.getCooldownPercent(stack, partial);
			int defaultMs = known().entrySet().stream()
					.filter(k -> k.getKey().getItem() == stack.getItem())
					.mapToInt(Map.Entry::getValue).findFirst().orElse(0);
			Estimate est = estimates.get(group);
			if (est == null || fraction > est.lastFraction + 0.05f) {
				est = new Estimate(fraction, defaultMs);
				estimates.put(group, est);
			}
			est.lastFraction = fraction;
			result.add(new Row(group.toString(), stack,stack.getHoverName().getString(), fraction, est.remaining(fraction)));
		}
		estimates.keySet().retainAll(seen.keySet());
		return result;
	}

	@Override
	public boolean hasContent() {
		// Called once per frame before measure(); the result is reused there.
		current = collect();
		return !current.isEmpty();
	}

	@Override
	protected void measure(boolean preview) {
		if (!current.isEmpty() || preview) {
			rows.update(current);
		}
		String right = current.isEmpty() ? "" : String.valueOf(current.size());
		width = animateWidth(Math.max(W, HudStyle.headerWidth(CAPTION, right)));
		height = HudStyle.TITLE_H + 3 + Math.round(Math.max(14, rows.height(ROW_H))) + 2;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		HudStyle.panel(g, p, x, y, width, height);
		String right = current.isEmpty() ? "" : String.valueOf(current.size());
		int top = HudStyle.header(g, p, HudIcon.TIMER, CAPTION, right, x, y, width);
		float empty = 1f - Math.min(1f, rows.height(ROW_H) / 14f);
		if (empty > 0.01f) {
			RenderUtil.withAlpha(empty, () -> RenderUtil.text(g, null, "Нет задержек", x + HudStyle.PAD, top + 3, p.textDim()));
		}
		float ry = top;
		for (AnimatedRows.Row<Row> ar : rows.rows()) {
			Row r = ar.value();
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(ar.shown(), () -> {
				// Item models can't fade, so they only show while the row is mostly visible.
				if (RenderUtil.alpha() > 0.6f) {
					g.item(r.stack(), x + HudStyle.PAD, rowY + 1);
				}
				int tx = x + HudStyle.PAD + 20;
				String time = r.seconds() >= 0 ? String.format("%.1fс", r.seconds()) : "…";
				int timeX = x + width - HudStyle.PAD - RenderUtil.width(time);
				RenderUtil.text(g, null, RenderUtil.ellipsize(r.name(), timeX - tx - 4, false), tx, rowY + 2, p.text());
				RenderUtil.text(g, null, time, timeX, rowY + 2, p.accent2());
				HudStyle.bar(g, p, tx, rowY + 13, x + width - HudStyle.PAD - tx, 2.5f, r.fraction(), p.accent());
			});
			ry += ROW_H * ar.open();
		}
	}
}
