package dev.elysium.visuals.client.gps;

import dev.elysium.visuals.client.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The GPS mark: a point (x, z) in the current world, set with {@code .gps} or by
 * AutoEventGPS. Shown by the GPS module (arrow on screen, pillar in the world);
 * removed once you get within {@link #ARRIVE_DISTANCE} blocks.
 */
public final class GpsTarget {
	public static final double ARRIVE_DISTANCE = 5;

	/** A mark; {@code label} is shown next to the distance ("GPS", "Аирдроп"…). */
	public record Mark(int x, int z, String label) {
	}

	private static Mark mark;

	private GpsTarget() {
	}

	public static Mark get() {
		return mark;
	}

	public static void set(int x, int z, String label) {
		mark = new Mark(x, z, label);
	}

	public static void clear() {
		mark = null;
	}

	/** Horizontal distance from the player to the mark, or -1 without a mark or player. */
	public static double distance() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (mark == null || p == null) {
			return -1;
		}
		double dx = mark.x() + 0.5 - p.getX(), dz = mark.z() + 0.5 - p.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Called every tick: removes the mark when you've arrived. */
	public static void tick() {
		double d = distance();
		if (d >= 0 && d < ARRIVE_DISTANCE) {
			String label = mark.label();
			mark = null;
			Notifications.alert("GPS", "Вы на месте" + (label.equals("GPS") ? "" : ": " + label));
		}
	}
}
