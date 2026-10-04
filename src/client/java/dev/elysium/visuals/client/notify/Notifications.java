package dev.elysium.visuals.client.notify;

import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.NotificationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Entry point for toasts. Every call is a no-op when the Notifications module
 * is off or the type is filtered out, so modules can call it freely.
 */
public final class Notifications {
	/** Toast categories (filterable in the module settings). */
	public enum Type { MODULES, CONFIG, PICKUP, MESSAGES }

	/** Accent kinds: the theme accent, a positive green, or a muted grey. */
	public enum Tone { ACCENT, POSITIVE, MUTED }

	private Notifications() {
	}

	private static NotificationsModule module() {
		NotificationsModule m = ModuleManager.get().find(NotificationsModule.class);
		return m != null && m.isEnabled() ? m : null;
	}

	public static void push(Type type, Tone tone, String title, String text, ItemStack icon) {
		NotificationsModule m = module();
		if (m != null && m.shows(type)) {
			m.push(new Toast(title, text, icon == null ? ItemStack.EMPTY : icon, tone));
		}
	}

	/** A message from a module (UseTracker, AutoAccept, …). */
	public static void module(String title, String text) {
		push(Type.MESSAGES, Tone.ACCENT, title, text, null);
	}

	/** Same with an item icon. */
	public static void module(String title, String text, ItemStack icon) {
		push(Type.MESSAGES, Tone.ACCENT, title, text, icon);
	}

	/** A message the player has to see: a toast, or the action bar when module toasts are off. */
	public static void alert(String title, String text) {
		NotificationsModule m = module();
		if (m != null && m.shows(Type.MESSAGES)) {
			m.push(new Toast(title, text, ItemStack.EMPTY, Tone.ACCENT));
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(title + ": " + text));
		}
	}

	public static void toggled(String moduleName, boolean on) {
		push(Type.MODULES, on ? Tone.POSITIVE : Tone.MUTED, moduleName, on ? "включён" : "выключен", null);
	}

	public static void config(String text) {
		push(Type.CONFIG, Tone.ACCENT, "Конфиг", text, null);
	}

	public static void pickup(ItemStack stack, int amount) {
		push(Type.PICKUP, Tone.ACCENT, "Подобрано", amount + " × " + stack.getHoverName().getString(), stack);
	}
}
