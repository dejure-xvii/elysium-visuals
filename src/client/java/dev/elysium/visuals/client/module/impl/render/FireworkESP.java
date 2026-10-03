package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.mixin.FireworkRocketEntityAccessor;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.ScreenProjector;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * A plate over every flying firework with its icon and the time left before it
 * bursts, followed by a trail of the same plates that shrink and fade.
 */
public class FireworkESP extends Module {
	private static final int TRAIL = 24;
	private static final int H = 16;

	private final NumberSetting interval = add(new NumberSetting("interval", "Интервал следа", 0.1, 0.03, 0.5, 0.01, " с"));
	private final NumberSetting trailLife = add(new NumberSetting("trail_life", "Время жизни следа", 0.8, 0.2, 3, 0.05, " с"));

	/** Per rocket: a ring buffer of past positions with the time they were recorded. */
	private static final class Track {
		final double[] x = new double[TRAIL], y = new double[TRAIL], z = new double[TRAIL];
		final long[] at = new long[TRAIL];
		int head, count;
		long lastMs;
		ItemStack icon = ItemStack.EMPTY;
		boolean seen;
	}

	private final Map<Integer, Track> tracks = new HashMap<>();
	private final ScreenProjector projector = new ScreenProjector();

	public FireworkESP() {
		super("firework_esp", "FireworkESP", "Плашки над летящими фейерверками со временем полёта и следом", Category.RENDER);
	}

	@Override
	protected void onDisable() {
		tracks.clear();
	}

	/** Seconds left in the air. The client never learns the exact lifetime, so use the game's average for its flight power. */
	private static float secondsLeft(FireworkRocketEntity rocket) {
		ItemStack stack = rocket.getItem();
		Fireworks fw = stack.get(DataComponents.FIREWORKS);
		int flight = fw == null ? 1 : fw.flightDuration() + 1;
		float lifetime = 10f * flight + 5.5f;
		int life = ((FireworkRocketEntityAccessor) rocket).elysium$getLife();
		return Math.max(0, (lifetime - life) / 20f);
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			tracks.clear();
			return;
		}
		long now = Util.getMillis();
		long step = Math.round(interval.get() * 1000), life = Math.round(trailLife.get() * 1000);
		for (Track t : tracks.values()) {
			t.seen = false;
		}
		projector.begin(g.guiWidth(), g.guiHeight());
		Palette p = ThemeManager.get().palette();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof FireworkRocketEntity rocket)) {
				continue;
			}
			Track t = tracks.computeIfAbsent(rocket.getId(), id -> new Track());
			t.seen = true;
			t.icon = rocket.getItem();
			double x = rocket.xo + (rocket.getX() - rocket.xo) * partialTick;
			double y = rocket.yo + (rocket.getY() - rocket.yo) * partialTick + 0.5;
			double z = rocket.zo + (rocket.getZ() - rocket.zo) * partialTick;
			if (now - t.lastMs >= step) {
				t.lastMs = now;
				t.x[t.head] = x;
				t.y[t.head] = y;
				t.z[t.head] = z;
				t.at[t.head] = now;
				t.head = (t.head + 1) % TRAIL;
				t.count = Math.min(TRAIL, t.count + 1);
			}
			// Trail first (oldest, smallest), then the live plate on top.
			for (int k = t.count - 1; k >= 1; k--) {
				int i = Math.floorMod(t.head - 1 - k, TRAIL);
				float age = (now - t.at[i]) / (float) life;
				if (age >= 1 || !projector.project(t.x[i], t.y[i], t.z[i])) {
					continue;
				}
				float f = 1 - age;
				plate(g, p, t.icon, null, projector.x, projector.y, 0.35f + 0.5f * f, f * f * 0.8f);
			}
			if (projector.project(x, y, z)) {
				String time = String.format(Locale.ROOT, "%.1fs", secondsLeft(rocket));
				plate(g, p, t.icon, time, projector.x, projector.y, 1f, 1f);
			}
		}
		// Rockets gone (exploded): let their trail finish fading, then forget them.
		Iterator<Map.Entry<Integer, Track>> it = tracks.entrySet().iterator();
		while (it.hasNext()) {
			Track t = it.next().getValue();
			if (!t.seen) {
				int newest = Math.floorMod(t.head - 1, TRAIL);
				if (t.count == 0 || now - t.at[newest] > life) {
					it.remove();
					continue;
				}
				for (int k = t.count - 1; k >= 0; k--) {
					int i = Math.floorMod(t.head - 1 - k, TRAIL);
					float age = (now - t.at[i]) / (float) life;
					if (age < 1 && projector.project(t.x[i], t.y[i], t.z[i])) {
						float f = 1 - age;
						plate(g, p, t.icon, null, projector.x, projector.y, 0.35f + 0.5f * f, f * f * 0.8f);
					}
				}
			}
		}
	}

	/** A themed plate with the item icon (and the time, for the live one), scaled around its centre. */
	private static void plate(GuiGraphicsExtractor g, Palette p, ItemStack icon, String text, float cx, float cy, float scale, float alpha) {
		int w = text == null ? H : H + 2 + RenderUtil.width(text) + 5;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		RenderUtil.withAlpha(alpha, () -> {
			HudStyle.panel(g, p, -w / 2, -H / 2, w, H);
			if (RenderUtil.alpha() > 0.5f) {
				g.pose().pushMatrix();
				g.pose().translate(-w / 2f + 2, -H / 2f + 2);
				g.pose().scale(0.75f, 0.75f);
				g.item(icon, 0, 0);
				g.pose().popMatrix();
			}
			if (text != null) {
				RenderUtil.text(g, null, text, -w / 2 + H + 1, -4, p.text());
			}
		});
		g.pose().popMatrix();
	}
}
