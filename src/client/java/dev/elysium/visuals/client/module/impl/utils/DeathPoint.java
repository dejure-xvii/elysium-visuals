package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.ListElement;
import dev.elysium.visuals.client.hud.style.HudData;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.util.Alerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.List;

/** Remembers where you died: a chat message with the coordinates and a HUD compass pointing back. */
public class DeathPoint extends Module {
	private final BooleanSetting chat = add(new BooleanSetting("chat", "Координаты в чат", true));
	private final BooleanSetting compass = add(new BooleanSetting("compass", "Стрелка и расстояние", true));
	private final NumberSetting clearDistance = add(new NumberSetting("clear_distance", "Убрать метку ближе чем", 4, 0, 30, 1, " бл."));

	private BlockPos pos;
	private ResourceKey<Level> dimension;
	private boolean wasDead;

	public DeathPoint() {
		super("death_point", "Death Point", "Запоминает место смерти и показывает путь к нему", Category.UTILS);
		enableByDefault();
		addHud(new Element()).visibleWhen(compass::isOn);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			pos = player.blockPosition();
			dimension = player.level().dimension();
			if (chat.isOn() && !DeathCoords.writesChat()) {
				Alerts.chat(Component.literal("[Elysium] ").withColor(0x8FDBFF)
						.append(Component.literal("Место смерти: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
								+ " (" + dimensionName(dimension) + ")").withColor(0xFFFFFF)));
			}
		}
		wasDead = dead;
		if (!dead && pos != null && player.level().dimension() == dimension && clearDistance.intValue() > 0
				&& distance(player) < clearDistance.intValue()) {
			pos = null;
		}
	}

	private double distance(LocalPlayer player) {
		double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static String dimensionName(ResourceKey<Level> dim) {
		if (dim == Level.NETHER) {
			return "Незер";
		}
		return dim == Level.END ? "Энд" : "Верхний мир";
	}

	/** Arrow towards the death point (relative to where the player looks), or a dot in another dimension. */
	private final HudData.Lead arrow = (g, p, x, y, size) -> {
		float cx = x + size / 2f, cy = y + size / 2f;
		LocalPlayer player = Minecraft.getInstance().player;
		if (pos != null && player != null && player.level().dimension() == dimension) {
			double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
			float yawTo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			float rel = Mth.wrapDegrees(yawTo - player.getYRot());
			// chevron(turn = 0) points right; -1 points up (forward).
			RenderUtil.chevron(g, cx, cy, rel / 90f - 1f, p.accent2());
		} else {
			RenderUtil.roundedRect(g, cx - 2, cy - 2, 4, 4, 2, p.accent2());
		}
	};

	/** The death point as an Interface block: arrow, coordinates and distance, in the selected style. */
	private final class Element extends ListElement {
		private static final HudData.Block BLOCK = new HudData.Block(HudData.Kind.INFO, "Место смерти", HudIcon.COORDS,
				"Точка", "Расстояние");

		Element() {
			super("death", "Место смерти", Anchor.TOP_LEFT);
		}

		@Override
		protected HudData.Block block() {
			return BLOCK;
		}

		@Override
		protected boolean headerWhenEmpty() {
			return false;
		}

		@Override
		protected List<HudData.Row> collect() {
			LocalPlayer player = Minecraft.getInstance().player;
			if (pos == null || player == null || player.isDeadOrDying()) {
				return List.of();
			}
			String where = player.level().dimension() == dimension
					? Math.round(distance(player)) + " блоков" : dimensionName(dimension);
			return List.of(HudData.Row.text("death", arrow, pos.getX() + " " + pos.getY() + " " + pos.getZ(), "", where, true));
		}

		@Override
		protected List<HudData.Row> sample() {
			return List.of(HudData.Row.text("death", arrow, "120 64 -340", "", "56 блоков", true));
		}
	}
}
