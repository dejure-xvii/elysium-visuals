package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * A local copy of you (skin, armor, held items) that only exists on your
 * client: a punching bag for testing visual modules. Hits are simulated here
 * (sound, hurt animation, crit particles, damage from your attack strength);
 * nothing is sent to the server. It "dies" and comes back half a second later.
 */
public class FakePlayer extends Module {
	/** Negative ids can't collide with server entities. */
	private static final int ENTITY_ID = -0x454C59;
	private static final int RESPAWN_TICKS = 10;

	private final ModeSetting spawnAt = add(new ModeSetting("spawn_at", "Где появляться",
			List.of(option("here", "На моём месте"), option("front", "В 2.5 блоках передо мной")), "front"));

	private FakePlayerEntity entity;
	private int deadTicks = -1;

	public FakePlayer() {
		super("fake_player", "FakePlayer", "Локальная копия тебя для проверки визуальных модулей", Category.UTILS);
	}

	public static FakePlayer instance() {
		return ModuleManager.get().find(FakePlayer.class);
	}

	/** Whether {@code e} is our fake player. */
	public static boolean isFake(Entity e) {
		return e instanceof FakePlayerEntity;
	}

	@Override
	protected void onEnable() {
		spawn();
	}

	@Override
	protected void onDisable() {
		despawn();
	}

	/** (Re)spawns the copy at the configured spot. */
	public boolean spawn() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return false;
		}
		despawn();
		FakePlayerEntity fake = new FakePlayerEntity(level, player.getGameProfile().name());
		fake.setId(ENTITY_ID);
		Vec3 pos = player.position();
		if (spawnAt.is("front")) {
			Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
			pos = pos.add(look.scale(2.5));
		}
		fake.snapTo(pos.x, pos.y, pos.z, player.getYRot() + 180, 0);
		fake.setYHeadRot(player.getYRot() + 180);
		fake.yBodyRot = player.getYRot() + 180;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			fake.setItemSlot(slot, player.getItemBySlot(slot).copy());
		}
		fake.setHealth(fake.getMaxHealth());
		level.addEntity(fake);
		entity = fake;
		deadTicks = -1;
		return true;
	}

	public void despawn() {
		Minecraft mc = Minecraft.getInstance();
		if (entity != null && mc.level != null) {
			mc.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
		}
		entity = null;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (entity == null) {
			if (mc.level != null && mc.player != null) {
				spawn(); // e.g. after changing worlds
			}
			return;
		}
		if (entity.level() != mc.level || entity.isRemoved()) {
			entity = null;
			return;
		}
		if (deadTicks >= 0 && ++deadTicks >= RESPAWN_TICKS) {
			// Back on its feet: full health, no death animation.
			entity.setHealth(entity.getMaxHealth());
			entity.deathTime = 0;
			entity.hurtTime = 0;
			deadTicks = -1;
		}
	}

	/**
	 * Attack damage of the held item. The client never applies item attribute
	 * modifiers to the player (only the server does), so add the main-hand
	 * item's damage modifiers to the base value ourselves.
	 */
	private static float attackDamage(Player attacker) {
		double base = attacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		double[] add = {0}, multiply = {1};
		attacker.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
				switch (modifier.operation()) {
					case ADD_VALUE -> add[0] += modifier.amount();
					case ADD_MULTIPLIED_BASE -> add[0] += base * modifier.amount();
					case ADD_MULTIPLIED_TOTAL -> multiply[0] *= 1 + modifier.amount();
				}
			}
		});
		return (float) Math.max(1, (base + add[0]) * multiply[0]);
	}

	/** Called (mixin) instead of sending the attack to the server. */
	public static void onAttacked(Player attacker, Entity target) {
		FakePlayer m = instance();
		if (m == null || m.entity != target || m.deadTicks >= 0) {
			return;
		}
		FakePlayerEntity fake = m.entity;
		float strength = attacker.getAttackStrengthScale(0.5f);
		float damage = attackDamage(attacker) * (0.2f + strength * strength * 0.8f);
		boolean crit = strength > 0.9f && attacker.fallDistance > 0 && !attacker.onGround() && !attacker.onClimbable()
				&& !attacker.isInWater() && !attacker.isPassenger();
		Minecraft mc = Minecraft.getInstance();
		if (crit) {
			damage *= 1.5f;
			mc.particleEngine.createTrackingEmitter(fake, ParticleTypes.CRIT);
		}
		ClientLevel level = mc.level;
		level.playLocalSound(fake.getX(), fake.getY(), fake.getZ(),
				crit ? SoundEvents.PLAYER_ATTACK_CRIT : strength > 0.9f ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
				SoundSource.PLAYERS, 1f, 1f, false);
		fake.animateHurt(0);
		fake.hurtTime = fake.hurtDuration = 10;
		float health = fake.getHealth() - damage;
		if (health <= 0) {
			fake.setHealth(0);
			level.playLocalSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1f, 1f, false);
			m.deadTicks = 0;
		} else {
			fake.setHealth(health);
			level.playLocalSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f, false);
		}
	}
}
