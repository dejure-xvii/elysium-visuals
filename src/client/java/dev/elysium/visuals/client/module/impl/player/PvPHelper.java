package dev.elysium.visuals.client.module.impl.player;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.FriendsModule;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import dev.elysium.visuals.client.module.setting.StringSetting;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * PvP helpers: a repeated call-out in chat, a chat black list, frames around
 * important hotbar items, and outlines on yourself / friends / a target
 * (only where the player is visible, not through walls; see {@code LevelRendererOutlineMixin}).
 */
public class PvPHelper extends Module {
	private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** Where the sender part of a chat line ends: "<Nick> …", "Nick: …", "Nick » …", "Nick → …", "Nick | …". */
	private static final Pattern SENDER_END = Pattern.compile("[>:»→|]");

	private final BooleanSetting shout = add(new BooleanSetting("shout", "Автоматически зазывать", false));
	private final StringSetting shoutText = add(new StringSetting("shout_text", "Сообщение", "!pvp z4", 100))
			.visibleWhen(shout::isOn);
	private final NumberSetting shoutInterval = add(new NumberSetting("shout_interval", "Интервал", 60, 30, 600, 5, " с"))
			.visibleWhen(shout::isOn);

	private final BooleanSetting blacklistOn = add(new BooleanSetting("blacklist_on", "Чёрный список", false));
	private final StringListSetting blacklist = add(new StringListSetting("blacklist", "Скрывать сообщения от", "Ник игрока…",
			name -> NICK.matcher(name).matches())).visibleWhen(blacklistOn::isOn);

	private final BooleanSetting slots = add(new BooleanSetting("slots", "Подсветка слотов", true));

	private final MultiSelectSetting glow = add(new MultiSelectSetting("glow", "Подсвечивать",
			List.of(option("self", "Себя"), option("friends", "Друзей"), option("target", "Таргет")), Set.of("friends", "target")));
	private final ColorSetting selfColor = add(new ColorSetting("self_color", "Цвет: себя", 0xFF6AE3FF))
			.visibleWhen(() -> glow.isSelected("self"));
	private final ColorSetting friendColor = add(new ColorSetting("friend_color", "Цвет: друзья", 0xFF5CE08A))
			.visibleWhen(() -> glow.isSelected("friends"));
	private final ColorSetting targetColor = add(new ColorSetting("target_color", "Цвет: таргет", 0xFFFF5470))
			.visibleWhen(() -> glow.isSelected("target"));
	private final StringSetting target = add(new StringSetting("target", "Таргет", "", 16))
			.visibleWhen(() -> glow.isSelected("target"));

	/** Set by the outline depth hook once it has run (checked by the game test). */
	public static boolean outlineHookRan;

	private long lastShoutMs;

	public PvPHelper() {
		super("pvp_helper", "PvPHelper", "Зазывалка, чёрный список, подсветка слотов и игроков", Category.PLAYER);
	}

	private static PvPHelper active() {
		PvPHelper m = ModuleManager.get().find(PvPHelper.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** The black list (editable while the module is off, for {@code .blacklist}); null before modules exist. */
	public static StringListSetting blacklist() {
		PvPHelper m = ModuleManager.get().find(PvPHelper.class);
		return m == null ? null : m.blacklist;
	}

	@Override
	protected void onEnable() {
		// The first call goes out after one interval, not the moment the module is switched on.
		lastShoutMs = Util.getMillis();
	}

	// --- Call-out -----------------------------------------------------------------------------

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!shout.isOn() || player == null || mc.getCurrentServer() == null) {
			return;
		}
		long now = Util.getMillis();
		if (now - lastShoutMs < Math.max(30, shoutInterval.get()) * 1000) {
			return;
		}
		lastShoutMs = now;
		String text = shoutText.get().trim();
		if (text.isEmpty()) {
			return;
		}
		if (text.startsWith("/")) {
			player.connection.sendCommand(text.substring(1));
		} else {
			player.connection.sendChat(text);
		}
	}

	// --- Black list -----------------------------------------------------------------------------

	/** True if the chat message comes from a black-listed player (called by a mixin; such lines aren't shown). */
	public static boolean hides(Component message) {
		PvPHelper m = active();
		if (m == null || !m.blacklistOn.isOn() || m.blacklist.get().isEmpty()) {
			return false;
		}
		String sender = sender(message.getString());
		return sender != null && m.blacklist.contains(sender);
	}

	/** The sender's nick of a chat line ("[VIP] Nick: hi" → "Nick"), or null if it doesn't look like a player message. */
	static String sender(String line) {
		String text = line.length() > 80 ? line.substring(0, 80) : line;
		Matcher end = SENDER_END.matcher(text);
		if (!end.find()) {
			return null;
		}
		String head = text.substring(0, end.start());
		String nick = null;
		Matcher n = NICK.matcher(head);
		while (n.find()) {
			nick = n.group(); // the last nick-like word before the separator (ranks come first)
		}
		return nick;
	}

	// --- Hotbar slots ---------------------------------------------------------------------------

	private static boolean important(ItemStack stack) {
		return stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.ENDER_PEARL);
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (!slots.isOn() || mc.player == null || mc.player.isSpectator()) {
			return;
		}
		int x0 = g.guiWidth() / 2 - 91, y = g.guiHeight() - 22;
		int c = ThemeColors.primary() | 0xFF000000;
		float pulse = 0.75f + 0.25f * (float) Math.sin(Util.getMillis() / 250.0);
		for (int i = 0; i < 9; i++) {
			if (important(mc.player.getInventory().getItem(i))) {
				float sx = x0 + 1 + i * 20 + 1, sy = y + 2;
				RenderUtil.roundedRect(g, sx, sy, 18, 18, 3, ColorUtil.withAlpha(c, (int) (0x30 * pulse)));
				RenderUtil.roundedOutline(g, sx, sy, 18, 18, 3, 1f, ColorUtil.withAlpha(c, (int) (0xE0 * pulse)));
			}
		}
	}

	// --- Outlines -------------------------------------------------------------------------------

	/** The outline color for {@code entity} (ARGB), or 0 if PvPHelper doesn't highlight it. */
	public static int outlineColor(Entity entity) {
		PvPHelper m = active();
		if (m == null || !(entity instanceof Player p)) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		String name = p.getGameProfile().name();
		if (p == mc.player) {
			return m.glow.isSelected("self") ? m.selfColor.argb() | 0xFF000000 : 0;
		}
		String t = m.target.get().trim();
		if (m.glow.isSelected("target") && !t.isEmpty() && t.equalsIgnoreCase(name)) {
			return m.targetColor.argb() | 0xFF000000;
		}
		if (m.glow.isSelected("friends") && FriendsModule.isFriend(name)) {
			return m.friendColor.argb() | 0xFF000000;
		}
		return 0;
	}

	/** True while some outline of ours may be drawn: outlines then stop at walls. */
	public static boolean outlinesActive() {
		PvPHelper m = active();
		return m != null && !m.glow.get().isEmpty();
	}
}
