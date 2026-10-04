package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.ListElement;
import dev.elysium.visuals.client.hud.style.HudData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Active potion effects: icon, name with level and the remaining time. */
public class BuffsElement extends ListElement {
	private static final HudData.Block BLOCK = new HudData.Block(HudData.Kind.BUFFS, "Эффекты", HudIcon.POTION,
			"Эффект", "Длительность");

	/** Longest duration seen per effect, so the time ring can show how much is left. */
	private final Map<String, Integer> fullDuration = new HashMap<>();

	public BuffsElement() {
		super("buffs", "Бафы", Anchor.BOTTOM_RIGHT);
	}

	@Override
	public boolean replacesVanillaEffects() {
		return true;
	}

	@Override
	protected HudData.Block block() {
		return BLOCK;
	}

	@Override
	protected List<HudData.Row> collect() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			fullDuration.clear();
			return List.of();
		}
		List<MobEffectInstance> effects = new ArrayList<>();
		for (MobEffectInstance e : mc.player.getActiveEffects()) {
			if (e.showIcon()) {
				effects.add(e);
			}
		}
		effects.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));

		List<HudData.Row> rows = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		for (MobEffectInstance e : effects) {
			String key = e.getEffect().getRegisteredName();
			String name = e.getEffect().value().getDisplayName().getString();
			if (e.getAmplifier() > 0) {
				name += " " + (e.getAmplifier() + 1);
			}
			int ticks = e.getDuration();
			int full = Math.max(ticks, fullDuration.getOrDefault(key, 0));
			seen.put(key, full);
			boolean infinite = e.isInfiniteDuration();
			rows.add(HudData.Row.timed(key, HudData.Lead.sprite(Hud.getMobEffectSprite(e.getEffect())), name,
					infinite ? Float.POSITIVE_INFINITY : ticks / 20f,
					infinite ? 1f : full > 0 ? ticks / (float) full : 0f,
					e.getEffect().value().isBeneficial()));
		}
		fullDuration.clear();
		fullDuration.putAll(seen);
		return rows;
	}

	@Override
	protected List<HudData.Row> sample() {
		return List.of(
				HudData.Row.timed("sample_speed", HudData.Lead.sprite(Hud.getMobEffectSprite(MobEffects.SPEED)),
						"Скорость 2", 91, 0.6f, true),
				HudData.Row.timed("sample_absorption", HudData.Lead.sprite(Hud.getMobEffectSprite(MobEffects.ABSORPTION)),
						"Поглощение 4", 11, 0.1f, true));
	}
}
