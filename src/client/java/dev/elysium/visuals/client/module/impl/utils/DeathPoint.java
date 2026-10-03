package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.Alerts;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

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
			if (chat.isOn()) {
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

	private final class Element extends HudElement {
		private String line = "";
		private String sub = "";

		Element() {
			super("death", "Место смерти", Anchor.TOP_LEFT);
		}

		@Override
		public boolean hasContent() {
			LocalPlayer player = Minecraft.getInstance().player;
			return pos != null && player != null && !player.isDeadOrDying();
		}

		@Override
		protected void measure(boolean preview) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (pos != null && player != null) {
				line = pos.getX() + " " + pos.getY() + " " + pos.getZ();
				sub = player.level().dimension() == dimension ? Math.round(distance(player)) + " блоков" : dimensionName(dimension);
			} else if (preview) {
				line = "120 64 -340";
				sub = "56 блоков";
			}
			width = HudStyle.PAD * 2 + 18 + Math.max(RenderUtil.widthBold(line), RenderUtil.width(sub)) + 4;
			height = 26;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			HudStyle.panel(g, p, x, y, width, height);
			float cx = x + HudStyle.PAD + 7, cy = y + height / 2f;
			RenderUtil.roundedRect(g, cx - 7, cy - 7, 14, 14, 7, ColorUtil.mulAlpha(p.accent(), 0.25f));
			LocalPlayer player = Minecraft.getInstance().player;
			if (pos != null && player != null && player.level().dimension() == dimension) {
				// Angle of the death point relative to where the player is looking (0 = straight ahead).
				double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
				float yawTo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
				float rel = Mth.wrapDegrees(yawTo - player.getYRot());
				// chevron(turn = 0) points right; -1 points up (forward).
				RenderUtil.chevron(g, cx, cy, rel / 90f - 1f, p.accent());
			} else {
				RenderUtil.roundedRect(g, cx - 2, cy - 2, 4, 4, 2, p.accent());
			}
			int tx = x + HudStyle.PAD + 18;
			RenderUtil.textBold(g, line, tx, y + 4, p.text());
			RenderUtil.text(g, null, sub, tx, y + 14, p.textDim());
		}
	}
}
