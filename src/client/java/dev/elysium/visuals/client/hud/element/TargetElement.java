package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.module.impl.utils.ScoreboardHealth;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The entity you're fighting, in one of three styles:
 * <ul>
 *   <li><b>Карточка</b> — glass card with an accent glow that flares on every hit,
 *       a large skin head, the nick, distance caption, health number, a two-layer
 *       health bar, an absorption bar and the target's armor and held item;</li>
 *   <li><b>Компактный</b> — one slim row: head, nick, health and the bar;</li>
 *   <li><b>Минимал</b> — no card, just a soft backdrop, nick, health and a hairline bar;</li>
 *   <li><b>Классика</b> — dark card, big head, light bar with the health number on a pointer,
 *       effects and gear floating above;</li>
 *   <li><b>Капсула</b> — small light-glass pill with a gear row and a round health ring.</li>
 * </ul>
 * The health bar drains in two layers like in fighting games: the colored bar
 * drops at once, a light trail follows after a short pause. The head shakes and
 * flashes red when the target takes damage. The card slides in, flows to the
 * new data when the target changes and fades out once you stop hitting.
 */
public class TargetElement extends HudElement {
	private static final int GOLD = 0xFFFBBF24;
	private static final int GOLD_LIGHT = 0xFFFFE9A8;
	/** How long the trail waits before it catches up with the health bar. */
	private static final long TRAIL_HOLD_MS = 420;
	private static final long SHAKE_MS = 320;
	private static final long HIT_GLOW_MS = 450;
	/** Height of the icon row above the "Классика" card. */
	private static final int CLASSIC_ICONS = 15;
	private static final EquipmentSlot[] GEAR = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final ModeSetting style;

	private LivingEntity shown;
	private ItemStack eggIcon = ItemStack.EMPTY;
	private boolean sample;

	private final SmoothValue health = new SmoothValue(1, 26f);
	private final SmoothValue trail = new SmoothValue(1, 5f);
	private final SmoothValue absorption = new SmoothValue(0, 14f);
	private final SmoothValue healthNumber = new SmoothValue(20, 18f);
	/** Slide/scale-in of the whole card. */
	private final SmoothValue appear = new SmoothValue(0, 11f);
	/** Content fade when switching to another target. */
	private final SmoothValue swap = new SmoothValue(1, 9f);
	private float lastFraction = -1;
	private long trailHoldUntil;
	private long damageMs;

	public TargetElement(ModeSetting style) {
		super("target", "Активный таргет", Anchor.CROSSHAIR);
		this.style = style;
	}

	@Override
	public boolean hasContent() {
		return TargetTracker.current() != null;
	}

	// --- State --------------------------------------------------------------

	@Override
	protected void measure(boolean preview) {
		LivingEntity target = TargetTracker.current();
		sample = target == null && preview;
		if (sample) {
			target = Minecraft.getInstance().player;
		}
		if (target != null && target != shown) {
			switchTo(target);
		}
		switch (style.get()) {
			case "compact" -> {
				width = 140;
				height = 32;
			}
			case "minimal" -> {
				width = 124;
				height = 24;
			}
			case "classic" -> {
				width = 156;
				height = CLASSIC_ICONS + 42;
			}
			case "capsule" -> {
				width = animateWidth(capsuleWidth());
				height = 26;
			}
			default -> {
				width = 178;
				height = 54;
			}
		}
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
	private void updateHealth(long now) {
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

	// --- Drawing ------------------------------------------------------------

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		if (shown == null) {
			return;
		}
		long now = Util.getMillis();
		updateHealth(now);
		float in = appear.update(hasContent() || preview ? 1f : 0f);
		float ease = 1 - (1 - in) * (1 - in) * (1 - in);
		float content = swap.update(1f);

		// Slides up a little and grows from 94 % while it appears.
		float cx = x + width / 2f, cy = y + height / 2f;
		float scale = 0.94f + 0.06f * ease;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + (1 - ease) * 8f);
		g.pose().scale(scale, scale);
		g.pose().translate(-cx, -cy);
		RenderUtil.withAlpha(0.35f + 0.65f * ease, () -> {
			switch (style.get()) {
				case "compact" -> drawCompact(g, p, now, content);
				case "minimal" -> drawMinimal(g, p, now, content);
				case "classic" -> drawClassic(g, p, now, content);
				case "capsule" -> drawCapsule(g, p, now, content);
				default -> drawCard(g, p, now, content);
			}
		});
		g.pose().popMatrix();
	}

	/** 0..1 flare of the accent glow right after our hit. */
	private float hitPulse(long now) {
		if (sample) {
			return 0f;
		}
		float t = (now - TargetTracker.lastHitMs()) / (float) HIT_GLOW_MS;
		return t >= 1f || t < 0f ? 0f : (1 - t) * (1 - t);
	}

	/** 0..1 red flash of the head after the target took damage. */
	private float damageFlash(long now) {
		float t = damageMs == 0 ? 1f : (now - damageMs) / (float) SHAKE_MS;
		return t >= 1f || t < 0f ? 0f : 1 - t;
	}

	private void drawCard(GuiGraphicsExtractor g, Palette p, long now, float content) {
		float pulse = hitPulse(now);
		float r = 10;
		// Accent glow along the edge, flaring on hits.
		RenderUtil.softGlow(g, x, y, width, height, r, ColorUtil.mulAlpha(p.accent(), 0.16f + 0.44f * pulse), 7 + Math.round(5 * pulse));
		HudStyle.panel(g, p, x, y, width, height, r);
		// A faint accent wash from the left, under the head.
		RenderUtil.roundedHGradient(g, x, y, width * 0.6f, height, r,
				ColorUtil.mulAlpha(p.accent(), 0.14f + 0.12f * pulse), ColorUtil.withAlpha(p.accent(), 0));
		RenderUtil.roundedOutline(g, x, y, width, height, r, 0, ColorUtil.mulAlpha(p.accent2(), 0.22f + 0.5f * pulse));

		int head = 40;
		int hx = x + 7, hy = y + 7;
		int tx = hx + head + 8;
		int right = x + width - 8;
		int slide = Math.round((1 - content) * 5);

		RenderUtil.withAlpha(content, () -> {
			drawHead(g, p, hx, hy, head, 9, now);

			// Health number (large) with a small "HP" caption.
			String hp = formatHealth(healthNumber.get());
			int capW = RenderUtil.captionWidth("HP");
			int numW = RenderUtil.width(hp, RenderUtil.Face.TITLE);
			int numX = right - capW - 3 - numW;
			RenderUtil.text(g, hp, RenderUtil.Face.TITLE, numX, y + 5, p.text());
			RenderUtil.caption(g, "HP", right - capW, y + 12, p.textFaint());

			// Nick (large).
			String name = displayName();
			RenderUtil.text(g, RenderUtil.ellipsize(name, numX - 8 - tx, RenderUtil.Face.TITLE), RenderUtil.Face.TITLE,
					tx + slide, y + 5, p.text());

			// Caption: distance, plus absorption in gold.
			String caption = distanceCaption();
			RenderUtil.caption(g, caption, tx + slide, y + 25, p.textDim());
			float abs = shown.getAbsorptionAmount();
			if (abs > 0) {
				String gold = String.format(Locale.ROOT, "+%s", formatHealth(abs));
				RenderUtil.caption(g, gold, tx + slide + RenderUtil.captionWidth(caption) + 6, y + 25, GOLD);
			}

			drawEquipment(g, right, y + 22, 10);
		});

		drawBar(g, p, tx, y + 40, right - tx, 5);
	}

	private void drawCompact(GuiGraphicsExtractor g, Palette p, long now, float content) {
		float pulse = hitPulse(now);
		float r = 8;
		RenderUtil.softGlow(g, x, y, width, height, r, ColorUtil.mulAlpha(p.accent(), 0.10f + 0.40f * pulse), 5 + Math.round(4 * pulse));
		HudStyle.panel(g, p, x, y, width, height, r);
		RenderUtil.roundedOutline(g, x, y, width, height, r, 0, ColorUtil.mulAlpha(p.accent2(), 0.15f + 0.5f * pulse));

		int head = 22;
		int hx = x + 5, hy = y + 5;
		int tx = hx + head + 6;
		int right = x + width - 6;
		RenderUtil.withAlpha(content, () -> {
			drawHead(g, p, hx, hy, head, 6, now);
			String hp = formatHealth(healthNumber.get());
			int hpW = RenderUtil.widthBold(hp);
			float abs = shown.getAbsorptionAmount();
			String gold = abs > 0 ? "+" + formatHealth(abs) : "";
			int goldW = gold.isEmpty() ? 0 : RenderUtil.width(gold) + 3;
			RenderUtil.textBold(g, hp, right - hpW, y + 6, healthColor(health.get()));
			if (!gold.isEmpty()) {
				RenderUtil.text(g, null, gold, right - hpW - goldW, y + 6, GOLD);
			}
			RenderUtil.textBold(g, RenderUtil.ellipsize(displayName(), right - hpW - goldW - 6 - tx, true), tx, y + 6, p.text());
		});
		drawBar(g, p, tx, y + 20, right - tx, 4);
	}

	private void drawMinimal(GuiGraphicsExtractor g, Palette p, long now, float content) {
		float pulse = hitPulse(now);
		// Only a soft dark backdrop for readability, no card.
		RenderUtil.softGlow(g, x, y, width, height, 7, 0x30000000, 6);
		RenderUtil.roundedRect(g, x, y, width, height, 7, 0x48000000);
		RenderUtil.roundedOutline(g, x, y, width, height, 7, 0, ColorUtil.mulAlpha(p.accent2(), 0.08f + 0.5f * pulse));

		int head = 14;
		int hx = x + 5, hy = y + 4;
		int tx = hx + head + 5;
		int right = x + width - 6;
		RenderUtil.withAlpha(content, () -> {
			drawHead(g, p, hx, hy, head, 4, now);
			String hp = formatHealth(healthNumber.get());
			int hpW = RenderUtil.width(hp);
			RenderUtil.text(g, null, hp, right - hpW, y + 5, healthColor(health.get()));
			RenderUtil.textBold(g, RenderUtil.ellipsize(displayName(), right - hpW - 6 - tx, true), tx, y + 5, p.text());
		});
		drawBar(g, p, tx, y + 16.5f, right - tx, 2.5f);
	}

	/**
	 * "Классика": a dark translucent card with a big head, the white nick and a
	 * long light health bar; the health number rides above the bar's end with a
	 * small pointer. Potion effects, armor and held items float in a row above.
	 */
	private void drawClassic(GuiGraphicsExtractor g, Palette p, long now, float content) {
		float pulse = hitPulse(now);
		int top = y + CLASSIC_ICONS;
		int ch = height - CLASSIC_ICONS;
		float r = 8;

		RenderUtil.withAlpha(content, () -> drawIconRow(g, x + 2, y + 1, 12));

		if (pulse > 0) {
			RenderUtil.softGlow(g, x, top, width, ch, r, ColorUtil.mulAlpha(p.accent(), 0.45f * pulse), 4 + Math.round(4 * pulse));
		}
		RenderUtil.softGlow(g, x, top, width, ch, r, 0x40000000, 6);
		// Always dark (the theme only tints it), so the white nick and light bar read on any theme.
		RenderUtil.roundedRect(g, x, top, width, ch, r, ColorUtil.withAlpha(ColorUtil.mixRgb(p.bgBottom(), 0xFF000000, 0.72f), 0xB8));
		RenderUtil.roundedGradient(g, x, top, width, ch, r, 0x0EFFFFFF, 0x00FFFFFF);
		RenderUtil.roundedOutline(g, x, top, width, ch, r, 0,
				ColorUtil.mix(0x22FFFFFF, ColorUtil.mulAlpha(p.accent2(), 0.7f), pulse));

		int head = ch - 8;
		int hx = x + 4, hy = top + 4;
		int tx = hx + head + 7;
		int right = x + width - 8;
		float bw = right - tx, bh = 4;
		float by = top + ch - 11;
		float k = 1f / (1f + absorption.get());
		float hpF = health.get() * k, trF = trail.get() * k, endF = hpF + absorption.get() * k;

		RenderUtil.withAlpha(content, () -> {
			drawHead(g, p, hx, hy, head, 7, now);
			RenderUtil.textBold(g, RenderUtil.ellipsize(displayName(), (int) bw, true), tx, top + 6, 0xFFFFFFFF);

			// Health number above the bar's current end, with a pointer that follows it.
			float abs = shown.getAbsorptionAmount();
			String num = formatHealth(healthNumber.get() + abs);
			int nw = RenderUtil.width(num);
			float endX = tx + bw * endF;
			int nx = Math.max(tx, Math.min(right - nw, Math.round(endX - nw / 2f)));
			RenderUtil.text(g, null, num, nx, Math.round(by) - 15, abs > 0 ? GOLD_LIGHT : 0xFFFFFFFF);
			pointer(g, Math.max(tx + 2, Math.min(right - 2, endX)), by - 1.6f, 4.4f, 2.6f, 0xE6FFFFFF);
		});

		// Bar: dark track, light trail, light health with an accent tint, gold absorption.
		RenderUtil.roundedRect(g, tx, by, bw, bh, bh / 2, 0x70000000);
		RenderUtil.roundedOutline(g, tx, by, bw, bh, bh / 2, 0, 0x16FFFFFF);
		if (trF > hpF + 0.002f) {
			RenderUtil.roundedRect(g, tx, by, Math.max(bh, bw * trF), bh, bh / 2, 0x66FFFFFF);
		}
		if (hpF > 0.002f) {
			int from = ColorUtil.mixRgb(p.accent2(), 0xFFFFFFFF, 0.5f);
			int to = ColorUtil.mixRgb(p.accent2(), 0xFFFFFFFF, 0.88f);
			// Low health turns the bar towards red.
			float danger = Math.max(0f, 1f - health.get() / 0.3f);
			from = ColorUtil.mix(from, Palette.DANGER, danger * 0.8f);
			to = ColorUtil.mix(to, Palette.DANGER, danger * 0.6f);
			float w = Math.max(bh, bw * hpF);
			RenderUtil.softGlow(g, tx, by, w, bh, bh / 2, ColorUtil.mulAlpha(to, 0.25f), 3);
			RenderUtil.roundedHGradient(g, tx, by, w, bh, bh / 2, ColorUtil.withAlpha(from, 0xFF), ColorUtil.withAlpha(to, 0xFF));
		}
		if (endF - hpF > 0.004f) {
			RenderUtil.roundedHGradient(g, tx + bw * hpF, by, Math.max(bh, bw * (endF - hpF)), bh, bh / 2, GOLD_LIGHT, GOLD);
		}
	}

	/** Small down-pointing triangle with its tip at (cx, tipY). */
	private static void pointer(GuiGraphicsExtractor g, float cx, float tipY, float w, float h, int color) {
		int steps = 5;
		float sh = h / steps;
		for (int i = 0; i < steps; i++) {
			float rw = w * (1f - i / (float) steps);
			RenderUtil.rect(g, cx - rw / 2, tipY - h + i * sh, rw, sh + 0.01f, color);
		}
	}

	/** Potion effects, then held items and armor (with their enchantment glint), left to right. */
	private void drawIconRow(GuiGraphicsExtractor g, int ix, int iy, int size) {
		for (MobEffectInstance e : shown.getActiveEffects()) {
			if (e.showIcon()) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), ix, iy, size, size, RenderUtil.alpha());
				ix += size + 2;
			}
		}
		drawItems(g, gear(), ix, iy, size, size + 1);
	}

	private List<ItemStack> gear() {
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : GEAR) {
			ItemStack stack = shown.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				items.add(stack);
			}
		}
		return items;
	}

	/** Items scaled to {@code size}; models can't fade, so they only show once mostly visible. */
	private static void drawItems(GuiGraphicsExtractor g, List<ItemStack> items, int ix, int iy, int size, int step) {
		if (RenderUtil.alpha() <= 0.6f) {
			return;
		}
		float s = size / 16f;
		for (ItemStack stack : items) {
			g.pose().pushMatrix();
			g.pose().translate(ix, iy);
			g.pose().scale(s, s);
			g.item(stack, 0, 0);
			g.pose().popMatrix();
			ix += step;
		}
	}

	private int capsuleWidth() {
		if (shown == null) {
			return 110;
		}
		int name = Math.min(92, RenderUtil.widthBold(displayName()));
		int items = gear().size() * 9 - 1;
		return 4 + 18 + 6 + Math.max(36, Math.max(name, items)) + 8 + 22 + 4;
	}

	/**
	 * "Капсула": a small pill of light glass with a little head, the nick, a row
	 * of gear icons and a round health ring with the number inside.
	 */
	private void drawCapsule(GuiGraphicsExtractor g, Palette p, long now, float content) {
		float pulse = hitPulse(now);
		float r = height / 2f;
		if (pulse > 0) {
			RenderUtil.softGlow(g, x, y, width, height, r, ColorUtil.mulAlpha(p.accent(), 0.4f * pulse), 4 + Math.round(4 * pulse));
		}
		RenderUtil.softGlow(g, x, y, width, height, r, 0x2C000000, 5);
		RenderUtil.roundedRect(g, x, y, width, height, r, ColorUtil.withAlpha(ColorUtil.mixRgb(p.bgTop(), 0xFF000000, 0.4f), 0x58));
		RenderUtil.roundedGradient(g, x, y, width, height, r, 0x30FFFFFF, 0x12FFFFFF);
		RenderUtil.roundedOutline(g, x, y, width, height, r, 0, ColorUtil.mix(0x50FFFFFF, ColorUtil.mulAlpha(p.accent2(), 0.8f), pulse));
		RenderUtil.fadedLine(g, x + r, y + RenderUtil.hairline(), width - 2 * r, RenderUtil.hairline(), 0x80FFFFFF);

		int head = 18;
		int hx = x + 4, hy = y + 4;
		int tx = hx + head + 6;
		float ringR = 10;
		float rcx = x + width - 4 - ringR, rcy = y + height / 2f;
		RenderUtil.withAlpha(content, () -> {
			drawHead(g, p, hx, hy, head, 6, now);
			RenderUtil.textBold(g, RenderUtil.ellipsize(displayName(), Math.round(rcx - ringR - 6 - tx), true), tx, y + 4, p.text());
			drawItems(g, gear(), tx, y + 15, 8, 9);
		});
		drawRing(g, p, rcx, rcy, ringR, 1.8f, content);
	}

	/** Thin ring filled clockwise from the top: trail, health and gold absorption; the number inside. */
	private void drawRing(GuiGraphicsExtractor g, Palette p, float cx, float cy, float radius, float t, float content) {
		RenderUtil.roundedOutline(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, t, ColorUtil.withAlpha(p.text(), 0x26));
		float k = 1f / (1f + absorption.get());
		float hpF = health.get() * k, trF = trail.get() * k, endF = hpF + absorption.get() * k;
		float rc = radius - t / 2;
		int c = healthColor(health.get());
		if (trF > hpF + 0.002f) {
			arc(g, cx, cy, rc, t, hpF, trF, ColorUtil.withAlpha(ColorUtil.mixRgb(c, 0xFFFFFFFF, 0.7f), 0xB0));
		}
		arc(g, cx, cy, rc, t, 0, hpF, c);
		arc(g, cx, cy, rc, t, hpF, endF, GOLD);
		String num = String.valueOf(Math.round(healthNumber.get() + shown.getAbsorptionAmount()));
		RenderUtil.withAlpha(content, () -> RenderUtil.text(g, num, RenderUtil.Face.SMALL,
				Math.round(cx - RenderUtil.width(num, RenderUtil.Face.SMALL) / 2f), Math.round(cy - 3.5f), p.text()));
	}

	/** Arc of the circle of radius {@code rc} from fraction {@code from} to {@code to} (0 = top, clockwise). */
	private static void arc(GuiGraphicsExtractor g, float cx, float cy, float rc, float t, float from, float to, int color) {
		if (to - from < 0.003f) {
			return;
		}
		int segments = Math.max(1, (int) Math.ceil((to - from) * 40));
		double prev = -Math.PI / 2 + from * Math.PI * 2;
		for (int i = 1; i <= segments; i++) {
			double a = -Math.PI / 2 + (from + (to - from) * i / segments) * Math.PI * 2;
			RenderUtil.stroke(g, cx + (float) Math.cos(prev) * rc, cy + (float) Math.sin(prev) * rc,
					cx + (float) Math.cos(a) * rc, cy + (float) Math.sin(a) * rc, t, color);
			prev = a;
		}
	}

	// --- Parts --------------------------------------------------------------

	/** Skin head (or spawn egg / initial) in a rounded tile; shakes and flashes red on damage. */
	private void drawHead(GuiGraphicsExtractor g, Palette p, int hx, int hy, int size, float radius, long now) {
		float flash = damageFlash(now);
		float shake = flash > 0 ? (float) Math.sin((1 - flash) * Math.PI * 6) * 2.2f * flash : 0f;
		g.pose().pushMatrix();
		g.pose().translate(shake, 0);
		if (flash > 0) {
			RenderUtil.softGlow(g, hx, hy, size, size, radius, ColorUtil.mulAlpha(Palette.DANGER, 0.55f * flash), 5);
		}
		RenderUtil.roundedRect(g, hx, hy, size, size, radius, ColorUtil.withAlpha(p.text(), 0x12));
		int inset = Math.max(2, Math.round(size * 0.1f));
		int face = size - inset * 2;
		int alpha = Math.round(255 * RenderUtil.alpha());
		if (shown instanceof AbstractClientPlayer player) {
			int tint = ColorUtil.mixRgb(0xFFFFFFFF, 0xFFFF6A80, 0.65f * flash);
			PlayerFaceExtractor.extractRenderState(g, player.getSkin(), hx + inset, hy + inset, face, ColorUtil.withAlpha(tint, alpha));
		} else if (!eggIcon.isEmpty()) {
			// Item models can't fade, so they only appear once the card is mostly visible.
			if (RenderUtil.alpha() > 0.6f) {
				float s = face / 16f;
				g.pose().pushMatrix();
				g.pose().translate(hx + inset, hy + inset);
				g.pose().scale(s, s);
				g.item(eggIcon, 0, 0);
				g.pose().popMatrix();
			}
		} else {
			String letter = displayName().isEmpty() ? "?" : displayName().substring(0, 1).toUpperCase(Locale.ROOT);
			RenderUtil.Face f = size >= 30 ? RenderUtil.Face.TITLE : RenderUtil.Face.BOLD;
			int lh = f == RenderUtil.Face.TITLE ? 13 : 8;
			RenderUtil.text(g, letter, f, hx + (size - RenderUtil.width(letter, f)) / 2, hy + (size - lh) / 2 - (f == RenderUtil.Face.TITLE ? 2 : 0), p.accent2());
		}
		if (flash > 0) {
			RenderUtil.roundedRect(g, hx, hy, size, size, radius, ColorUtil.mulAlpha(Palette.DANGER, 0.32f * flash));
		}
		RenderUtil.roundedOutline(g, hx, hy, size, size, radius, 0,
				ColorUtil.mix(ColorUtil.withAlpha(p.text(), 0x30), Palette.DANGER, flash));
		g.pose().popMatrix();
	}

	/**
	 * Two-layer health bar: a light trail that catches up after a pause, the
	 * colored bar (green → yellow → red) and the gold absorption bar on top.
	 */
	private void drawBar(GuiGraphicsExtractor g, Palette p, float bx, float by, float bw, float bh) {
		float hp = health.get();
		float tr = trail.get();
		float r = bh / 2;
		RenderUtil.roundedRect(g, bx, by, bw, bh, r, ColorUtil.withAlpha(p.text(), 0x16));
		int c = healthColor(hp);
		if (tr > hp + 0.002f) {
			RenderUtil.roundedRect(g, bx, by, Math.max(bh, bw * tr), bh, r, ColorUtil.withAlpha(ColorUtil.mixRgb(c, 0xFFFFFFFF, 0.7f), 0xB0));
		}
		if (hp > 0.002f) {
			float w = Math.max(bh, bw * hp);
			RenderUtil.softGlow(g, bx, by, w, bh, r, ColorUtil.mulAlpha(c, 0.3f), Math.max(2, Math.round(bh)));
			RenderUtil.roundedHGradient(g, bx, by, w, bh, r, ColorUtil.mixRgb(c, 0xFFFFFFFF, 0.35f), c);
		}
		float abs = absorption.get();
		if (abs > 0.004f) {
			RenderUtil.roundedHGradient(g, bx, by, Math.max(bh, bw * abs), bh, r, GOLD_LIGHT, GOLD);
		}
	}

	/** Armor and the held item as small icons, right-aligned at {@code right}. */
	private void drawEquipment(GuiGraphicsExtractor g, int right, int top, int size) {
		// Item models can't fade, so they only appear once the card is mostly visible.
		if (RenderUtil.alpha() <= 0.6f) {
			return;
		}
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = shown.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				items.add(stack);
			}
		}
		float s = size / 16f;
		int step = size + 2;
		int ix = right - items.size() * step + 2;
		for (ItemStack stack : items) {
			g.pose().pushMatrix();
			g.pose().translate(ix, top);
			g.pose().scale(s, s);
			g.item(stack, 0, 0);
			g.pose().popMatrix();
			ix += step;
		}
	}

	private String displayName() {
		return shown.getName().getString();
	}

	/** "4.2 м" to the target (upper-cased by the caption). */
	private String distanceCaption() {
		var player = Minecraft.getInstance().player;
		if (player == null || sample) {
			return "Превью";
		}
		return String.format(Locale.ROOT, "%.1f м", player.distanceTo(shown));
	}

	private static String formatHealth(float v) {
		float rounded = Math.round(v * 2) / 2f;
		return rounded == (int) rounded
				? String.valueOf((int) rounded)
				: String.format(Locale.ROOT, "%.1f", rounded);
	}

	/** Green → yellow → red as health goes down. */
	private static int healthColor(float f) {
		return f > 0.5f
				? ColorUtil.mix(Palette.WARN, Palette.OK, (f - 0.5f) * 2f)
				: ColorUtil.mix(Palette.DANGER, Palette.WARN, f * 2f);
	}
}
