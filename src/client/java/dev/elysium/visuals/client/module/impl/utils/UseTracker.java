package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Notifications when players nearby eat a golden apple, drink a potion, pop a
 * totem etc. The client sees when someone starts and stops using an item; if
 * they held it for its full use time, it was consumed.
 */
public class UseTracker extends Module {
	private final MultiSelectSetting track = add(new MultiSelectSetting("track", "Отслеживать",
			List.of(
					option("gapple", "Золотое яблоко"),
					option("egapple", "Зачарованное яблоко"),
					option("potion", "Зелья"),
					option("totem", "Тотем"),
					option("food", "Другая еда")),
			Set.of("gapple", "egapple", "potion", "totem")));
	private final BooleanSetting self = add(new BooleanSetting("self", "Себя тоже", false));
	private final NumberSetting radius = add(new NumberSetting("radius", "Радиус", 32, 8, 128, 1, " бл."));

	/** Per player: the item being used and the tick it started. */
	private record Use(ItemStack stack, long startTick) {
	}

	private final Map<UUID, Use> using = new HashMap<>();
	private long tick;

	public UseTracker() {
		super("use_tracker", "UseTracker", "Уведомления: кто рядом съел яблоко, выпил зелье, сработал тотем", Category.UTILS);
	}

	@Override
	protected void onDisable() {
		using.clear();
	}

	private boolean inRange(LivingEntity e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		if (e == mc.player) {
			return self.isOn();
		}
		return e.distanceToSqr(mc.player) <= radius.get() * radius.get();
	}

	/** Which tracked category an item belongs to, or null. */
	private String category(ItemStack stack) {
		String c;
		if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			c = "egapple";
		} else if (stack.is(Items.GOLDEN_APPLE)) {
			c = "gapple";
		} else if (stack.is(Items.POTION)) {
			c = "potion";
		} else if (stack.has(DataComponents.FOOD)) {
			c = "food";
		} else {
			return null;
		}
		return track.isSelected(c) ? c : null;
	}

	@Override
	public void onTick(Minecraft mc) {
		tick++;
		if (mc.level == null) {
			using.clear();
			return;
		}
		for (Player p : mc.level.players()) {
			UUID id = p.getUUID();
			Use use = using.get(id);
			if (p.isUsingItem()) {
				ItemStack stack = p.getUseItem();
				if (use == null || !ItemStack.isSameItem(use.stack(), stack)) {
					using.put(id, new Use(stack.copy(), tick));
				}
			} else if (use != null) {
				using.remove(id);
				// Held for (about) the whole use time: it was eaten / drunk.
				int needed = use.stack().getUseDuration(p);
				if (tick - use.startTick() >= needed - 3 && inRange(p)) {
					String c = category(use.stack());
					if (c != null) {
						Notifications.module(p.getGameProfile().name(), verb(c) + " " + use.stack().getHoverName().getString(), use.stack());
					}
				}
			}
		}
		using.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}

	private static String verb(String category) {
		return category.equals("potion") ? "выпил(а)" : "съел(а)";
	}

	/** Called (mixin) when any entity's totem of undying activates. */
	public static void onTotem(LivingEntity entity) {
		UseTracker m = ModuleManager.get().find(UseTracker.class);
		if (m != null && m.isEnabled() && m.track.isSelected("totem") && m.inRange(entity)) {
			String name = entity instanceof Player p ? p.getGameProfile().name() : entity.getName().getString();
			Notifications.module(name, "Сработал тотем бессмертия", new ItemStack(Items.TOTEM_OF_UNDYING));
		}
	}
}
