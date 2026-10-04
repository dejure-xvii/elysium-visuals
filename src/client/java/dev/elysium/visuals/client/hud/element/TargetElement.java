package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.hud.style.HudData;
import dev.elysium.visuals.client.hud.style.InterfaceStyle;
import dev.elysium.visuals.client.hud.style.Skin;
import dev.elysium.visuals.client.module.impl.utils.ScoreboardHealth;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Locale;

/**
 * The entity you're fighting, drawn in the selected Interface style. The
 * health bar drains in two layers: the bar drops at once, a light trail
 * follows after a short pause. The head shakes and flashes red when the target
 * takes damage. The block slides in, flows to the new data when the target
 * changes and fades out once you stop hitting.
 */
public class TargetElement extends HudElement implements HudData.Target {
	private static final int DANGER = 0xFFFF5C7A;
	/** How long the trail waits before it catches up with the health bar. */
	private static final long TRAIL_HOLD_MS = 420;
	private static final long SHAKE_MS = 320;

	private LivingEntity shown;
	private ItemStack eggIcon = ItemStack.EMPTY;

	private final SmoothValue health = new SmoothValue(1, 16f);
	private final SmoothValue trail = new SmoothValue(1, 8f);
	private final SmoothValue absorption = new SmoothValue(0, 14f);
	private final SmoothValue healthNumber = new SmoothValue(20, 16f);
	/** Slide/scale-in of the whole block. */
	private final SmoothValue appear = new SmoothValue(0, 14f);
	/** Content fade when switching to another target. */
	private final SmoothValue swap = new SmoothValue(1, 14f);
	private float lastFraction = -1;
	private long trailHoldUntil;
	private long damageMs;
	private long now;

	public TargetElement() {
		super("target", "Активный таргет", Anchor.CROSSHAIR);
	}

	@Override
	public boolean hasContent() {
		return TargetTracker.current() != null;
	}

	// --- State --------------------------------------------------------------

	@Override
	protected void measure(boolean preview) {
		LivingEntity target = TargetTracker.current();
		if (target == null && preview) {
			target = Minecraft.getInstance().player;
		}
		if (target != null && target != shown) {
			switchTo(target);
		}
		Skin.Size size = InterfaceStyle.current().measureTarget(this);
		width = animateWidth(size.width());
		height = animateHeight(size.height());
	}

	private void switchTo(LivingEntity target) {
		boolean wasVisible = shown != null && appear.get() > 0.05f;
		shown = target;
		eggIcon = SpawnEggItem.byId(target.getType()).map(ItemStack::new).orElse(ItemStack.EMPTY);
		float f = fraction(target);
		lastFraction = f;
		trailHoldUntil = 0;
		damageMs = 0;
		if (wasVisible) {
			// Flow to the new target: the bars glide from the old values, the content fades in.
			swap.set(0);
			trail.set(Math.max(health.get(), f));
		} else {
			health.set(f);
			trail.set(f);
			absorption.set(absorptionFraction(target));
			healthNumber.set(currentHealth(target));
			swap.set(1);
		}
	}

	private static float fraction(LivingEntity e) {
		float max = Math.max(1f, ScoreboardHealth.maxHealth(e));
		return e.isDeadOrDying() ? 0f : Math.max(0f, Math.min(1f, ScoreboardHealth.health(e) / max));
	}

	private static float currentHealth(LivingEntity e) {
		return e.isDeadOrDying() ? 0f : Math.max(0f, ScoreboardHealth.health(e));
	}

	private static float absorptionFraction(LivingEntity e) {
		float max = Math.max(1f, ScoreboardHealth.maxHealth(e));
		return Math.max(0f, Math.min(1f, e.getAbsorptionAmount() / max));
	}

	/** Advances the health animations once per frame. */
	private void updateHealth() {
		float f = fraction(shown);
		if (lastFraction >= 0 && f < lastFraction - 0.0005f) {
			damageMs = now;
			trailHoldUntil = now + TRAIL_HOLD_MS;
		}
		lastFraction = f;
		health.update(f);
		if (health.get() >= trail.get()) {
			trail.set(health.get()); // healing: the trail just follows
		} else if (now >= trailHoldUntil) {
			trail.update(health.get());
		}
		absorption.update(absorptionFraction(shown));
		healthNumber.update(currentHealth(shown));
	}

	// --- HudData.Target -----------------------------------------------------

	@Override
	public String name() {
		return shown == null ? "" : shown.getName().getString();
	}

	@Override
	public float health() {
		return health.get();
	}

	@Override
	public float trail() {
		return trail.get();
	}

	@Override
	public float absorption() {
		return absorption.get();
	}

	@Override
	public float healthNumber() {
		return healthNumber.get();
	}

	@Override
	public float content() {
		return swap.get();
	}

	/** 0..1 red flash of the head after the target took damage. */
	private float damageFlash() {
		float t = damageMs == 0 ? 1f : (now - damageMs) / (float) SHAKE_MS;
		return t >= 1f || t < 0f ? 0f : 1 - t;
	}

	@Override
	public void drawHead(GuiGraphicsExtractor g, Palette p, int hx, int hy, int size, float radius) {
		float flash = damageFlash();
		float shake = flash > 0 ? (float) Math.sin((1 - flash) * Math.PI * 6) * Math.max(1f, size / 14f) * flash : 0f;
		g.pose().pushMatrix();
		g.pose().translate(shake, 0);
		RenderUtil.roundedRect(g, hx, hy, size, size, radius, ColorUtil.withAlpha(p.text(), 0x12));
		int alpha = Math.round(255 * RenderUtil.alpha());
		if (shown instanceof AbstractClientPlayer player) {
			int tint = ColorUtil.mixRgb(0xFFFFFFFF, 0xFFFF6A80, 0.65f * flash);
			PlayerFaceExtractor.extractRenderState(g, player.getSkin(), hx, hy, size, ColorUtil.withAlpha(tint, alpha));
		} else if (!eggIcon.isEmpty()) {
			// Item models can't fade, so they only appear once the block is mostly visible.
			if (RenderUtil.alpha() > 0.6f) {
				int inset = Math.max(1, Math.round(size * 0.1f));
				float s = (size - inset * 2) / 16f;
				g.pose().pushMatrix();
				g.pose().translate(hx + inset, hy + inset);
				g.pose().scale(s, s);
				g.item(eggIcon, 0, 0);
				g.pose().popMatrix();
			}
		} else {
			String n = name();
			String letter = n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.ROOT);
			RenderUtil.text(g, letter, RenderUtil.Face.BOLD, hx + (size - RenderUtil.width(letter, RenderUtil.Face.BOLD)) / 2,
					hy + (size - 8) / 2, p.accent2());
		}
		if (flash > 0) {
			RenderUtil.roundedRect(g, hx, hy, size, size, radius, ColorUtil.mulAlpha(DANGER, 0.32f * flash));
		}
		g.pose().popMatrix();
	}

	// --- Drawing ------------------------------------------------------------

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		if (shown == null) {
			return;
		}
		now = Util.getMillis();
		updateHealth();
		float in = appear.update(hasContent() || preview ? 1f : 0f);
		float ease = 1 - (1 - in) * (1 - in) * (1 - in);
		swap.update(1f);

		// Slides up a little and grows from 96 % while it appears.
		float cx = x + width / 2f, cy = y + height / 2f;
		float scale = 0.96f + 0.04f * ease;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + (1 - ease) * 6f);
		g.pose().scale(scale, scale);
		g.pose().translate(-cx, -cy);
		RenderUtil.withAlpha(0.35f + 0.65f * ease,
				() -> InterfaceStyle.current().drawTarget(g, p, this, x, y, width, height));
		g.pose().popMatrix();
	}
}
