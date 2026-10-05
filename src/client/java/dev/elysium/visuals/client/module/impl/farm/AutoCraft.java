package dev.elysium.visuals.client.module.impl.farm;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.impl.farm.AutoCraftRecipes.Ingredient;
import dev.elysium.visuals.client.module.impl.farm.AutoCraftRecipes.Recipe;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.notify.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafts the chosen item over and over while a crafting table is open: puts
 * one set of ingredients into the grid, waits for the result and shift-clicks
 * it into the inventory. Stops (with a notification) when the ingredients run
 * out or the inventory is full.
 */
public class AutoCraft extends Module {
	private final ModeSetting recipe = add(new ModeSetting("recipe", "Что крафтить", options(), AutoCraftRecipes.ALL.getFirst().id()));
	private final NumberSetting delay = add(new NumberSetting("delay", "Задержка между крафтами", 2, 0, 20, 1, " т."));

	private enum State { PLACE, WAIT_RESULT, STOPPED }

	private State state = State.PLACE;
	private int wait, timeout, crafted;
	private int menuId = -1;
	private String recipeId;

	public AutoCraft() {
		super("auto_craft", "AutoCraft", "Автокрафт выбранного предмета, пока открыт верстак", Category.FARM);
	}

	private static List<MultiSelectSetting.Option> options() {
		List<MultiSelectSetting.Option> list = new ArrayList<>();
		for (Recipe r : AutoCraftRecipes.ALL) {
			list.add(MultiSelectSetting.option(r.id(), r.label()));
		}
		return list;
	}

	@Override
	protected void onEnable() {
		reset();
	}

	private void reset() {
		state = State.PLACE;
		wait = 0;
		crafted = 0;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || !(player.containerMenu instanceof CraftingMenu menu)) {
			menuId = -1;
			return;
		}
		// A new table window or another recipe: start over.
		if (menu.containerId != menuId || !recipe.get().equals(recipeId)) {
			menuId = menu.containerId;
			recipeId = recipe.get();
			reset();
		}
		if (state == State.STOPPED) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		Recipe r = AutoCraftRecipes.byId(recipe.get());
		switch (state) {
			case PLACE -> place(mc, menu, r);
			case WAIT_RESULT -> takeResult(mc, menu, r);
			default -> {
			}
		}
	}

	private void click(Minecraft mc, CraftingMenu menu, int slot, int button, ContainerInput input) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
	}

	private void place(Minecraft mc, CraftingMenu menu, Recipe r) {
		// Leftovers in the grid or on the cursor go back to the inventory first.
		if (!menu.getCarried().isEmpty()) {
			Slot empty = findEmpty(menu);
			if (empty == null) {
				stop("Инвентарь полон", "Некуда положить предмет с курсора");
				return;
			}
			click(mc, menu, empty.index, 0, ContainerInput.PICKUP);
			return;
		}
		for (Slot s : menu.getInputGridSlots()) {
			if (s.hasItem()) {
				click(mc, menu, s.index, 0, ContainerInput.QUICK_MOVE);
				if (s.hasItem()) {
					stop("Инвентарь полон", "Не удалось убрать предметы из сетки");
				}
				return;
			}
		}
		// Enough of everything for one craft?
		for (Ingredient ing : r.distinct()) {
			if (count(menu, ing) < r.count(ing)) {
				stop("Ресурсы закончились", "Нужно: " + ing.name() + (crafted > 0 ? " · скрафчено: " + crafted : ""));
				return;
			}
		}
		if (findEmpty(menu) == null && findStackable(menu, r) == null) {
			stop("Инвентарь полон", "Некуда сложить " + r.label() + (crafted > 0 ? " · скрафчено: " + crafted : ""));
			return;
		}
		// One item of each ingredient into its cell: pick the stack up, right-click the cells, put the rest back.
		List<Slot> grid = menu.getInputGridSlots();
		for (Ingredient ing : r.distinct()) {
			int needed = r.count(ing);
			for (Slot src : inventorySlots(menu)) {
				if (needed <= 0) {
					break;
				}
				ItemStack stack = src.getItem();
				if (stack.isEmpty() || !ing.test().test(stack)) {
					continue;
				}
				click(mc, menu, src.index, 0, ContainerInput.PICKUP);
				for (int cell = 0; cell < 9 && needed > 0 && !menu.getCarried().isEmpty(); cell++) {
					if (r.grid()[cell] == ing && !grid.get(cell).hasItem()) {
						click(mc, menu, grid.get(cell).index, 1, ContainerInput.PICKUP);
						needed--;
					}
				}
				click(mc, menu, src.index, 0, ContainerInput.PICKUP);
			}
		}
		state = State.WAIT_RESULT;
		timeout = 20;
	}

	private void takeResult(Minecraft mc, CraftingMenu menu, Recipe r) {
		ItemStack result = menu.getResultSlot().getItem();
		if (!result.isEmpty() && r.result().test(result)) {
			click(mc, menu, menu.getResultSlot().index, 0, ContainerInput.QUICK_MOVE);
			crafted++;
			state = State.PLACE;
			wait = delay.intValue();
			return;
		}
		if (--timeout <= 0) {
			stop("Крафт не получился", "Результат не появился. Проверьте ингредиенты");
		}
	}

	private void stop(String title, String text) {
		state = State.STOPPED;
		Notifications.alert("AutoCraft: " + title, text);
	}

	private static List<Slot> inventorySlots(CraftingMenu menu) {
		List<Slot> list = new ArrayList<>();
		for (Slot s : menu.slots) {
			if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
				list.add(s);
			}
		}
		return list;
	}

	private static int count(CraftingMenu menu, Ingredient ing) {
		int n = 0;
		for (Slot s : inventorySlots(menu)) {
			if (!s.getItem().isEmpty() && ing.test().test(s.getItem())) {
				n += s.getItem().getCount();
			}
		}
		return n;
	}

	private static Slot findEmpty(CraftingMenu menu) {
		for (Slot s : inventorySlots(menu)) {
			if (!s.hasItem()) {
				return s;
			}
		}
		return null;
	}

	/** A stack of the result with room left. */
	private static Slot findStackable(CraftingMenu menu, Recipe r) {
		for (Slot s : inventorySlots(menu)) {
			ItemStack st = s.getItem();
			if (!st.isEmpty() && r.result().test(st) && st.getCount() < st.getMaxStackSize()) {
				return s;
			}
		}
		return null;
	}
}
