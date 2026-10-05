package dev.elysium.visuals.client.module.impl.farm;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Recipes AutoCraft knows. To add one: put a {@link Recipe} into {@link #ALL}
 * with a 3×3 pattern (row by row, null = empty cell) and the expected result.
 */
public final class AutoCraftRecipes {
	private AutoCraftRecipes() {
	}

	/** An ingredient: what matches it and a name for messages. */
	public record Ingredient(String name, Predicate<ItemStack> test) {
		static Ingredient of(Item item, String name) {
			return new Ingredient(name, s -> s.is(item));
		}
	}

	/**
	 * @param grid   nine cells, row by row; null = empty
	 * @param result what appears in the result slot
	 */
	public record Recipe(String id, String label, Ingredient[] grid, Predicate<ItemStack> result) {
		public List<Ingredient> distinct() {
			return Arrays.stream(grid).filter(java.util.Objects::nonNull).distinct().toList();
		}

		public int count(Ingredient ingredient) {
			return (int) Arrays.stream(grid).filter(i -> i == ingredient).count();
		}
	}

	private static final Ingredient PEARL = Ingredient.of(Items.ENDER_PEARL, "Жемчуг Эндера");
	private static final Ingredient BLAZE_POWDER = Ingredient.of(Items.BLAZE_POWDER, "Огненный порошок");
	private static final Ingredient SNOWBALL = Ingredient.of(Items.SNOWBALL, "Снежки");
	private static final Ingredient ARROW = Ingredient.of(Items.ARROW, "Стрелы");
	private static final Ingredient HARMING_POTION = new Ingredient("Оседающее зелье вреда", s -> {
		if (!s.is(Items.LINGERING_POTION)) {
			return false;
		}
		PotionContents c = s.get(DataComponents.POTION_CONTENTS);
		return c != null && (c.is(Potions.HARMING) || c.is(Potions.STRONG_HARMING));
	});

	public static final List<Recipe> ALL = List.of(
			new Recipe("eye_of_ender", "Око Эндера", new Ingredient[]{
					PEARL, BLAZE_POWDER, null,
					null, null, null,
					null, null, null}, s -> s.is(Items.ENDER_EYE)),
			new Recipe("snow_block", "Блок снега", new Ingredient[]{
					SNOWBALL, SNOWBALL, null,
					SNOWBALL, SNOWBALL, null,
					null, null, null}, s -> s.is(Items.SNOW_BLOCK)),
			new Recipe("harming_arrow", "Стрела вреда", new Ingredient[]{
					ARROW, ARROW, ARROW,
					ARROW, HARMING_POTION, ARROW,
					ARROW, ARROW, ARROW}, s -> s.is(Items.TIPPED_ARROW)));

	public static Recipe byId(String id) {
		return ALL.stream().filter(r -> r.id().equals(id)).findFirst().orElse(ALL.getFirst());
	}
}
