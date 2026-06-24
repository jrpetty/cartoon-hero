package com.claude.automata.recipe;

import com.claude.automata.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Recipes for the Circuit Assembler — a multi-input machine that builds the
 * Logic Circuit (and future advanced components) which gate higher-tier
 * machines. Matching is exact-multiset, like the Fabricator.
 */
public final class CircuitRecipes {

	public static final class Recipe {
		public final Map<Item, Integer> ingredients;
		private final Item resultItem;
		private final int resultCount;

		Recipe(Map<Item, Integer> ingredients, Item resultItem, int resultCount) {
			this.ingredients = ingredients;
			this.resultItem = resultItem;
			this.resultCount = resultCount;
		}

		public Item resultItem() {
			return resultItem;
		}

		public int resultCount() {
			return resultCount;
		}
	}

	private static final List<Recipe> RECIPES = new ArrayList<>();

	private CircuitRecipes() {
	}

	private static void add(Item result, int count, Object... pairs) {
		Map<Item, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.merge((Item) pairs[i], (Integer) pairs[i + 1], Integer::sum);
		}
		RECIPES.add(new Recipe(map, result, count));
	}

	static {
		add(ModItems.LOGIC_CIRCUIT.get(), 1, Items.COPPER_INGOT, 1, Items.REDSTONE, 2, Items.GOLD_NUGGET, 1);
		add(ModItems.LOGIC_CIRCUIT.get(), 2, ModItems.COPPER_DUST.get(), 1, Items.REDSTONE, 2, Items.GLOWSTONE_DUST, 1);
	}

	public static Recipe match(Map<Item, Integer> inputs) {
		if (inputs.isEmpty()) {
			return null;
		}
		for (Recipe recipe : RECIPES) {
			if (recipe.ingredients.equals(inputs)) {
				return recipe;
			}
		}
		return null;
	}

	public static ItemStack result(Recipe recipe) {
		return new ItemStack(recipe.resultItem(), recipe.resultCount());
	}
}
