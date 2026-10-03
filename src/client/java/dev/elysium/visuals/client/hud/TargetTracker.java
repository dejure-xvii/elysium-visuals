package dev.elysium.visuals.client.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Remembers the living entities the local player hit, and the last one. */
public final class TargetTracker {
	/** How long the target stays shown after the last hit. */
	public static final long SHOW_MS = 3000;

	private static LivingEntity target;
	private static long lastHitMs;
	/** Last hit time per entity (weak: entities that are gone drop out by themselves). */
	private static final Map<LivingEntity, Long> hits = new WeakHashMap<>();

	private TargetTracker() {
	}

	static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && entity instanceof LivingEntity living) {
				onHit(living);
			}
			return InteractionResult.PASS;
		});
	}

	/** Records a hit (also used for entities that only exist on the client, like the fake player). */
	public static void onHit(LivingEntity living) {
		target = living;
		lastHitMs = Util.getMillis();
		hits.put(living, lastHitMs);
	}

	/** The current target, or null if nothing was hit recently. */
	public static LivingEntity current() {
		if (target == null || Util.getMillis() - lastHitMs > SHOW_MS) {
			return null;
		}
		return target;
	}

	/** Time of our last hit on anything (Util.getMillis), 0 if none yet. */
	public static long lastHitMs() {
		return lastHitMs;
	}

	/** True if {@code entity} was hit by us within the last {@code ms} milliseconds. */
	public static boolean hitWithin(LivingEntity entity, long ms) {
		Long t = hits.get(entity);
		return t != null && Util.getMillis() - t <= ms;
	}

	/** Entities hit recently (live view; don't modify). */
	public static Map<LivingEntity, Long> recentHits() {
		return hits;
	}
}
