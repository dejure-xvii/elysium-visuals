package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Active potion effects: icon, name with level, remaining time and a thin time bar. */
public class BuffsElement extends HudElement {
	private static final int ROW_H = 18;
	private static final int ICON = 10;
	private static final String CAPTION = "Эффекты";

	private record Row(String key, Identifier sprite, String name, String time, float fraction, boolean ending, boolean beneficial) {
	}

	private final AnimatedRows<Row> rows = new AnimatedRows<>(Row::key);
	/** Longest duration seen per effect, so the bar can show how much is left. */
	private final Map<String, Integer> fullDuration = new HashMap<>();
	private String right = "";

	public BuffsElement() {
		super("buffs", "Бафы", Anchor.BOTTOM_RIGHT);
	}

	private static List<MobEffectInstance> effects() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return List.of();
		}
		List<MobEffectInstance> list = new ArrayList<>();
		for (MobEffectInstance e : mc.player.getActiveEffects()) {
			if (e.showIcon()) {
				list.add(e);
			}
		}
		list.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));
		return list;
	}

	@Override
	public boolean hasContent() {
		return !effects().isEmpty();
	}

	@Override
	public boolean replacesVanillaEffects() {
		return true;
	}

	private List<Row> collect() {
		List<Row> current = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		for (MobEffectInstance e : effects()) {
			String key = e.getEffect().getRegisteredName();
			String name = e.getEffect().value().getDisplayName().getString();
			if (e.getAmplifier() > 0) {
				name += " " + Component.translatable("enchantment.level." + (e.getAmplifier() + 1)).getString();
			}
			boolean infinite = e.isInfiniteDuration();
			int ticks = e.getDuration();
			int full = Math.max(ticks, fullDuration.getOrDefault(key, 0));
			seen.put(key, full);
			int seconds = ticks / 20;
			float fraction = infinite ? 1f : full > 0 ? ticks / (float) full : 0f;
			current.add(new Row(key, Hud.getMobEffectSprite(e.getEffect()), name,
					infinite ? "∞" : HudStyle.formatSeconds(seconds), fraction, !infinite && seconds <= 10,
					e.getEffect().value().isBeneficial()));
		}
		fullDuration.clear();
		fullDuration.putAll(seen);
		return current;
	}

	@Override
	protected void measure(boolean preview) {
		List<Row> current = collect();
		if (!current.isEmpty() || preview) {
			rows.update(current);
			right = current.isEmpty() ? "" : String.valueOf(current.size());
		}
		int w = Math.max(118, HudStyle.headerWidth(CAPTION, right));
		for (AnimatedRows.Row<Row> r : rows.rows()) {
			Row row = r.value();
			w = Math.max(w, HudStyle.PAD * 2 + ICON + 6 + RenderUtil.width(row.name()) + 12 + RenderUtil.width(row.time()));
		}
		width = animateWidth(w);
		height = HudStyle.TITLE_H + 3 + Math.round(Math.max(13, rows.height(ROW_H))) + 3;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		HudStyle.panel(g, p, x, y, width, height);
		int top = HudStyle.header(g, p, HudIcon.POTION, CAPTION, right, x, y, width);
		float empty = 1f - Math.min(1f, rows.height(ROW_H) / 13f);
		if (empty > 0.01f) {
			RenderUtil.withAlpha(empty, () -> RenderUtil.text(g, null, "Нет эффектов", x + HudStyle.PAD, top + 3, p.textDim()));
		}
		float pulse = 0.55f + 0.45f * (float) Math.sin(Util.getMillis() / 120.0);
		float ry = top;
		for (AnimatedRows.Row<Row> r : rows.rows()) {
			Row row = r.value();
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> {
				int ix = x + HudStyle.PAD;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite(), ix, rowY + 1, ICON, ICON, RenderUtil.alpha());
				int tx = ix + ICON + 6;
				int nameColor = row.beneficial() ? p.text() : ColorUtil.mix(p.text(), Palette.DANGER, 0.6f);
				RenderUtil.text(g, null, row.name(), tx, rowY + 2, nameColor);
				int timeColor = row.ending() ? ColorUtil.mulAlpha(Palette.DANGER, pulse) : p.textDim();
				RenderUtil.text(g, null, row.time(), x + width - HudStyle.PAD - RenderUtil.width(row.time()), rowY + 2, timeColor);
				HudStyle.bar(g, p, tx, rowY + 12.5f, x + width - HudStyle.PAD - tx, 2f, row.fraction(),
						row.beneficial() ? p.accent() : Palette.DANGER);
			});
			ry += ROW_H * r.open();
		}
	}
}
