package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.ListElement;
import dev.elysium.visuals.client.hud.style.HudData;
import net.minecraft.client.Minecraft;
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
 * the remaining time and how much of it is left.
 *
 * <p>The game only exposes the cooldown as a fraction, so the total length is
 * estimated from how fast that fraction drops (and a known default until then).
 */
public class CooldownsElement extends ListElement {
	private static final HudData.Block BLOCK = new HudData.Block(HudData.Kind.COOLDOWNS, "Задержки", HudIcon.TIMER,
			"Предмет", "Время");

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

	private final Map<Identifier, Estimate> estimates = new HashMap<>();

	public CooldownsElement() {
		super("cooldowns", "Задержки", Anchor.BOTTOM_RIGHT);
	}

	@Override
	protected HudData.Block block() {
		return BLOCK;
	}

	@Override
	protected List<HudData.Row> collect() {
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

		List<HudData.Row> result = new ArrayList<>();
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
			float remaining = est.remaining(fraction);
			result.add(HudData.Row.timed(group.toString(), HudData.Lead.item(stack), stack.getHoverName().getString(),
					remaining < 0 ? Float.NaN : remaining, fraction, true));
		}
		estimates.keySet().retainAll(seen.keySet());
		return result;
	}

	@Override
	protected List<HudData.Row> sample() {
		return List.of(HudData.Row.timed("sample_pearl", HudData.Lead.item(new ItemStack(Items.ENDER_PEARL)),
				new ItemStack(Items.ENDER_PEARL).getHoverName().getString(), 0.7f, 0.7f, true));
	}
}
