package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.notify.Notifications;
import dev.elysium.visuals.client.render.ScreenProjector;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * End crystals colored by danger: red while you are inside their blast
 * (deeper the closer to the full hit), green outside. The damage follows the
 * vanilla explosion: distance, blocks in between, difficulty, armor,
 * Protection / Blast Protection and Resistance.
 */
public class CustomCrystal extends Module {
	private static final float POWER = 6f;

	private final BooleanSetting zone = add(new BooleanSetting("zone", "Подсветка зоны взрыва", true));
	private final ColorSetting customColor = add(new ColorSetting("custom_color", "Кастомный цвет", 0xFFB48CFF)).visibleWhen(() -> !zone.isOn());
	private final BooleanSetting showDamage = add(new BooleanSetting("show_damage", "Урон над кристаллом", true));
	private final BooleanSetting block = add(new BooleanSetting("block", "Запрет взрыва рядом", true));
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Порог урона", 8, 1, 40, 0.5, " ед.")).visibleWhen(block::isOn);

	private final ScreenProjector projector = new ScreenProjector();
	private long lastWarning;

	public CustomCrystal() {
		super("custom_crystal", "CustomCrystal", "Цвет кристаллов Энда по опасности взрыва для вас", Category.RENDER);
	}

	private static CustomCrystal active() {
		CustomCrystal m = ModuleManager.get().find(CustomCrystal.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** Tint for a crystal's model (ARGB), or -1 to leave it alone. */
	public static int tint(EndCrystal crystal) {
		CustomCrystal m = active();
		Player player = Minecraft.getInstance().player;
		if (m == null || player == null) {
			return -1;
		}
		if (!m.zone.isOn()) {
			return m.customColor.argb() | 0xFF000000;
		}
		float dmg = damage(player, crystal.position());
		if (dmg <= 0) {
			return 0xFF5BE38A;
		}
		float max = Math.max(1f, damage(player, player.position(), 1f));
		return ColorUtil.mix(0xFFFFB0B0, 0xFFFF1E1E, Math.min(1f, dmg / max));
	}

	/** Called before an attack: true cancels it (the crystal would hurt too much). */
	public static boolean blocksAttack(Entity target) {
		CustomCrystal m = active();
		Player player = Minecraft.getInstance().player;
		if (m == null || player == null || !m.block.isOn() || !(target instanceof EndCrystal crystal) || player.isCreative()) {
			return false;
		}
		float dmg = damage(player, crystal.position());
		if (dmg < m.threshold.get()) {
			return false;
		}
		if (Util.getMillis() - m.lastWarning > 1500) {
			m.lastWarning = Util.getMillis();
			Notifications.alert("Взрыв отменён", String.format(Locale.ROOT, "Кристалл нанесёт вам %.1f урона", dmg));
		}
		return true;
	}

	/** Damage an end crystal at {@code center} would do to {@code player}. */
	public static float damage(Player player, Vec3 center) {
		return damage(player, center, -1f);
	}

	/** @param fullHit 0..1 forces point-blank with that exposure (the maximum), negative traces the real one */
	private static float damage(Player player, Vec3 center, float fullHit) {
		float diameter = POWER * 2;
		double dist = fullHit >= 0 ? 0 : Math.sqrt(player.distanceToSqr(center)) / diameter;
		if (dist > 1) {
			return 0;
		}
		float exposure = fullHit >= 0 ? fullHit : ServerExplosion.getSeenPercent(center, player);
		double impact = (1 - dist) * exposure;
		if (impact <= 0) {
			return 0;
		}
		float dmg = (float) ((impact * impact + impact) / 2 * 7 * diameter + 1);
		switch (player.level().getDifficulty()) {
			case PEACEFUL -> dmg = 0;
			case EASY -> dmg = Math.min(dmg / 2 + 1, dmg);
			case HARD -> dmg *= 1.5f;
			default -> {
			}
		}
		DamageSource source = player.damageSources().explosion(null, null);
		dmg = CombatRules.getDamageAfterAbsorb(player, dmg, source, player.getArmorValue(),
				(float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			dmg *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2f);
		}
		dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, protection(player));
		return Math.max(0, dmg);
	}

	/** Enchantment protection against explosions: Protection 1 per level, Blast Protection 2 per level (max 20). */
	private static float protection(Player player) {
		var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> prot = lookup.get(Enchantments.PROTECTION).orElse(null);
		Holder<Enchantment> blast = lookup.get(Enchantments.BLAST_PROTECTION).orElse(null);
		int epf = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				epf += level(prot, stack) + 2 * level(blast, stack);
			}
		}
		return Math.min(20, epf);
	}

	private static int level(Holder<Enchantment> e, ItemStack stack) {
		return e == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(e, stack);
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (!showDamage.isOn() || mc.level == null || mc.player == null) {
			return;
		}
		Palette p = ThemeManager.get().palette();
		projector.begin(g.guiWidth(), g.guiHeight());
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof EndCrystal crystal) || e.distanceToSqr(mc.player) > 48 * 48) {
				continue;
			}
			Vec3 pos = e.getPosition(partialTick);
			if (!projector.project(pos.x, pos.y + 2.6, pos.z)) {
				continue;
			}
			float dmg = damage(mc.player, crystal.position());
			String text = dmg <= 0 ? "0" : String.format(Locale.ROOT, "%.1f", dmg);
			int color = dmg <= 0 ? 0xFF5BE38A : dmg >= threshold.get() ? 0xFFFF5C7A : 0xFFFFC46B;
			int w = RenderUtil.width(text, RenderUtil.Face.BOLD) + 12;
			float bx = projector.x - w / 2f, by = projector.y - 7;
			RenderUtil.roundedRect(g, bx, by, w, 13, 6, ColorUtil.withAlpha(p.bgBottom(), 0xB0));
			RenderUtil.roundedOutline(g, bx, by, w, 13, 6, 0, ColorUtil.mulAlpha(color, 0.7f));
			RenderUtil.text(g, text, RenderUtil.Face.BOLD, Math.round(bx + 6), Math.round(by + 3), color);
		}
	}
}
