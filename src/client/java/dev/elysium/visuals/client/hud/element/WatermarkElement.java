package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.impl.utils.NameProtect;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Launcher-style watermark: the gradient "E" logo, "Elysium" bold and
 * "Visuals" light, then the selected info (nick, FPS, ping, time, server,
 * coordinates) separated by thin dividers — an icon, the value and a muted unit.
 */
public class WatermarkElement extends HudElement {
	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int HEIGHT = 20;
	private static final int LOGO = 12;
	private static final int PAD = 5;
	/** Space taken by a divider between two parts. */
	private static final int SEP = 13;
	private static final int ICON_W = 12;

	/** One info part: icon, value (theme text) and an optional muted unit. {@code raw}: NameProtect leaves it alone. */
	private record Info(HudIcon icon, String value, String unit, boolean raw) {
		int width() {
			int w = ICON_W + (raw ? RenderUtil.widthRaw(value) : RenderUtil.width(value));
			return unit.isEmpty() ? w : w + 3 + RenderUtil.width(unit);
		}
	}

	/** Color of the "Visuals" word; 0 = the theme's lilac accent. */
	private final IntSupplier titleColor;
	private final BooleanSetting seconds;
	private final BooleanSetting showNick;
	private final MultiSelectSetting info;
	private List<Info> parts = List.of();

	public WatermarkElement(IntSupplier titleColor, BooleanSetting seconds, BooleanSetting showNick, MultiSelectSetting info) {
		super("watermark", "Ватермарка", Anchor.TOP_LEFT);
		this.titleColor = titleColor;
		this.seconds = seconds;
		this.showNick = showNick;
		this.info = info;
	}

	private List<Info> collect() {
		Minecraft mc = Minecraft.getInstance();
		List<Info> list = new ArrayList<>();
		if (showNick.isOn()) {
			// NameProtect replaces the nick unless it is told to leave the watermark alone (raw).
			list.add(new Info(HudIcon.PLAYER, mc.getUser().getName(), "", !NameProtect.hidesInWatermark()));
		}
		if (info.isSelected("fps")) {
			list.add(new Info(HudIcon.FPS, String.valueOf(mc.getFps()), "fps", false));
		}
		if (info.isSelected("ping")) {
			String ping = "—";
			if (mc.getConnection() != null && mc.player != null) {
				PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (pi != null) {
					ping = String.valueOf(pi.getLatency());
				}
			}
			list.add(new Info(HudIcon.PING, ping, "ms", false));
		}
		if (info.isSelected("time")) {
			list.add(new Info(HudIcon.TIME, LocalTime.now().format(seconds.isOn() ? HH_MM_SS : HH_MM), "", false));
		}
		if (info.isSelected("server")) {
			ServerData server = mc.getCurrentServer();
			String name = mc.getConnection() == null ? "Меню" : server == null ? "Одиночная игра" : server.ip;
			list.add(new Info(HudIcon.SERVER, name, "", false));
		}
		if (info.isSelected("coords") && mc.player != null) {
			list.add(new Info(HudIcon.COORDS, mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ(), "", false));
		}
		return list;
	}

	private static int leadWidth() {
		return LOGO + 6 + RenderUtil.brandWidth();
	}

	@Override
	protected void measure(boolean preview) {
		parts = collect();
		int w = PAD * 2 + 1 + leadWidth();
		for (Info i : parts) {
			w += SEP + i.width();
		}
		width = animateWidth(w);
		height = HEIGHT;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		HudStyle.panel(g, p, x, y, width, height);
		// Content is clipped to the card while its width animates.
		g.enableScissor(x, y, x + width, y + height);
		int lx = x + PAD + 1;
		RenderUtil.logo(g, lx, y + (HEIGHT - LOGO) / 2f, LOGO, p, false);
		int tx = lx + LOGO + 6;
		int ty = y + (HEIGHT - 8) / 2;
		RenderUtil.text(g, "Elysium", RenderUtil.Face.HEAVY, tx, ty, p.text());
		int vx = tx + RenderUtil.width("Elysium", RenderUtil.Face.HEAVY) + 3;
		int c = titleColor.getAsInt();
		RenderUtil.text(g, "Visuals", RenderUtil.Face.LIGHT, vx, ty, c == 0 ? p.accent2() : c);
		tx = lx + leadWidth();

		int iconColor = ColorUtil.mulAlpha(p.accent2(), 0.9f);
		for (Info i : parts) {
			// Thin divider, brightest in the middle.
			RenderUtil.roundedGradient(g, tx + SEP / 2f - 0.5f, y + 5, RenderUtil.hairline(), HEIGHT / 2f - 5, 0,
					ColorUtil.withAlpha(p.border(), 0), ColorUtil.mulAlpha(p.text(), 0.22f));
			RenderUtil.roundedGradient(g, tx + SEP / 2f - 0.5f, y + HEIGHT / 2f, RenderUtil.hairline(), HEIGHT / 2f - 5, 0,
					ColorUtil.mulAlpha(p.text(), 0.22f), ColorUtil.withAlpha(p.border(), 0));
			tx += SEP;
			i.icon().draw(g, tx + 4, y + HEIGHT / 2f, iconColor);
			tx += ICON_W;
			if (i.raw()) {
				RenderUtil.textRaw(g, i.value(), tx, ty, p.text());
				tx += RenderUtil.widthRaw(i.value());
			} else {
				RenderUtil.text(g, i.value(), RenderUtil.Face.REGULAR, tx, ty, p.text());
				tx += RenderUtil.width(i.value());
			}
			if (!i.unit().isEmpty()) {
				tx += 3;
				RenderUtil.text(g, i.unit(), RenderUtil.Face.REGULAR, tx, ty, p.textDim());
				tx += RenderUtil.width(i.unit());
			}
		}
		g.disableScissor();
	}
}
