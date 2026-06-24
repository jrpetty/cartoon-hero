package com.claude.automata.recipe;

import com.claude.automata.registry.ModBlocks;
import com.claude.automata.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Fabricator's recipe book.
 *
 * <p>Recipes are shapeless: each is a multiset of input items mapped to a single
 * output. The Fabricator crafts when the items loaded into its input slots
 * exactly match one recipe's multiset — predictable behaviour that suits an
 * automated machine fed by hoppers (one Fabricator per recipe is the intended
 * factory pattern).
 *
 * <p>These recipes intentionally cover the items that are otherwise locked away
 * with the disabled vanilla crafting table (chest, hopper, bucket) plus this
 * mod's own machines, components and the Pulsar Multi-Tool.
 */
public final class FabricatorRecipes {

	/** One shapeless recipe: required input counts -> produced stack. */
	public static final class Recipe {
		public final Map<Item, Integer> ingredients;
		private final Item resultItem;
		private final int resultCount;
		public final int totalIngredients;

		Recipe(Map<Item, Integer> ingredients, Item resultItem, int resultCount) {
			this.ingredients = ingredients;
			this.resultItem = resultItem;
			this.resultCount = resultCount;
			int sum = 0;
			for (int c : ingredients.values()) {
				sum += c;
			}
			this.totalIngredients = sum;
		}

		public Item resultItem() {
			return resultItem;
		}

		public int resultCount() {
			return resultCount;
		}

		public ItemStack newResult() {
			return new ItemStack(resultItem, resultCount);
		}
	}

	private static final List<Recipe> RECIPES = new ArrayList<>();

	private FabricatorRecipes() {
	}

	private static Map<Item, Integer> ingredients(Object... pairs) {
		Map<Item, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.merge((Item) pairs[i], (Integer) pairs[i + 1], Integer::sum);
		}
		return map;
	}

	private static void add(Item result, int count, Object... ingredientPairs) {
		RECIPES.add(new Recipe(ingredients(ingredientPairs), result, count));
	}

	static {
		// --- Machine components ---
		add(ModItems.IRON_GEAR.get(), 1, Items.IRON_INGOT, 4);
		add(ModItems.MACHINE_FRAME.get(), 1, Items.IRON_INGOT, 4, ModItems.IRON_GEAR.get(), 2);

		// --- Machines (so you can build more) ---
		// The Forge Core and Dynamo need no iron, so they can be made before you can smelt.
		add(ModBlocks.FORGE_CORE.get().asItem(), 1, Items.COBBLESTONE, 8, Items.COAL, 1);
		add(ModBlocks.GENERATOR.get().asItem(), 1, Items.COBBLESTONE, 8, Items.REDSTONE, 1);
		add(ModBlocks.THERMAL_GENERATOR.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.IRON_INGOT, 4, Items.BUCKET, 1);
		add(ModBlocks.SOLAR_ARRAY.get().asItem(), 1, Items.GLASS, 4, Items.REDSTONE, 2, ModItems.IRON_GEAR.get(), 1);
		add(ModBlocks.CAPACITOR.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.REDSTONE, 4, ModItems.COPPER_DUST.get(), 2);
		add(ModBlocks.CONDUIT.get().asItem(), 8, Items.COPPER_INGOT, 1, Items.REDSTONE, 2);
		add(ModBlocks.PYLON.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.COPPER_INGOT, 2, Items.REDSTONE, 2);
		add(ModBlocks.FABRICATOR.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.COBBLESTONE, 4);
		add(ModBlocks.CIRCUIT_ASSEMBLER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.COPPER_INGOT, 3, Items.REDSTONE, 2);
		add(ModBlocks.CRUSHER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 2);
		add(ModBlocks.SAWMILL.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.IRON_INGOT, 1);
		add(ModBlocks.RECYCLER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 2, Items.REDSTONE, 1);
		add(ModBlocks.FLUID_PUMP.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.BUCKET, 1, ModItems.IRON_GEAR.get(), 2);
		add(ModBlocks.MINER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.IRON_INGOT, 4);
		add(ModBlocks.BLOCK_BREAKER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.COBBLESTONE, 2);
		add(ModBlocks.BLOCK_PLACER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.REDSTONE, 2);
		add(ModBlocks.COLLECTOR.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.HOPPER, 1, ModItems.IRON_GEAR.get(), 1);
		add(ModBlocks.XP_COLLECTOR.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.LOGIC_CIRCUIT.get(), 1, Items.GLASS, 2);
		add(ModBlocks.HARVESTER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.IRON_INGOT, 2);
		add(ModBlocks.RANCHER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.WHEAT, 2);
		add(ModBlocks.SENTRY.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.LOGIC_CIRCUIT.get(), 1, Items.IRON_INGOT, 2);
		add(ModBlocks.TREE_FARM.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.OAK_LOG, 2);
		add(ModBlocks.ROUTER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.HOPPER, 1, Items.REDSTONE, 2);
		add(ModBlocks.DRONE_BAY.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.LOGIC_CIRCUIT.get(), 1, Items.HOPPER, 1);
		add(ModBlocks.SORTER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.HOPPER, 2, Items.REDSTONE, 1);
		add(ModBlocks.CONVEYOR.get().asItem(), 4, Items.IRON_INGOT, 1, Items.REDSTONE, 1);
		add(ModBlocks.DRAWER.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.CHEST, 1, Items.GLASS, 1);
		add(ModBlocks.VOID.get().asItem(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.LAVA_BUCKET, 1);

		// --- Logistics tool ---
		add(ModItems.LOGISTICS_WRENCH.get(), 1, ModItems.IRON_GEAR.get(), 1, Items.IRON_INGOT, 1);

		// --- Upgrade modules ---
		add(ModItems.SPEED_UPGRADE.get(), 1, ModItems.IRON_GEAR.get(), 2, Items.REDSTONE, 2, Items.SUGAR, 1);
		add(ModItems.EFFICIENCY_UPGRADE.get(), 1, ModItems.IRON_GEAR.get(), 2, Items.REDSTONE, 2, ModItems.GOLD_DUST.get(), 1);

		// --- Use for the Ash byproduct ---
		add(Items.BLACK_DYE, 1, ModItems.ASH.get(), 4);

		// --- Automation unlocks (locked behind the disabled crafting table) ---
		add(Items.CHEST, 1, Items.OAK_PLANKS, 8);
		add(Items.HOPPER, 1, Items.IRON_INGOT, 5, Items.CHEST, 1);
		add(Items.BUCKET, 1, Items.IRON_INGOT, 3);

		// --- The reimagined tool ---
		add(ModItems.PULSAR_MULTITOOL.get(), 1, ModItems.MACHINE_FRAME.get(), 1, Items.IRON_INGOT, 2);

		// --- Charged gear (gated behind the Logic Circuit) ---
		add(ModItems.POWERED_DRILL.get(), 1, ModItems.MACHINE_FRAME.get(), 1, ModItems.LOGIC_CIRCUIT.get(), 1, Items.IRON_INGOT, 2);
		add(ModItems.PORTABLE_CHARGER.get(), 1, ModItems.LOGIC_CIRCUIT.get(), 1, ModItems.COPPER_DUST.get(), 2, Items.REDSTONE, 4);
		// --- Drone Bay batteries (range tiers) ---
		add(ModItems.DRONE_BATTERY.get(), 1, Items.COPPER_INGOT, 2, Items.REDSTONE, 2, ModItems.LOGIC_CIRCUIT.get(), 1);
		add(ModItems.DRONE_BATTERY_ADVANCED.get(), 1, Items.GOLD_INGOT, 2, Items.REDSTONE, 4, ModItems.LOGIC_CIRCUIT.get(), 1);
		add(ModItems.DRONE_BATTERY_ELITE.get(), 1, Items.DIAMOND, 1, Items.REDSTONE, 4, ModItems.LOGIC_CIRCUIT.get(), 2);
	}

	/**
	 * Find the recipe whose ingredient multiset exactly equals {@code inputs}.
	 * When several could match, the most expensive (most ingredients) wins.
	 */
	public static Recipe match(Map<Item, Integer> inputs) {
		if (inputs.isEmpty()) {
			return null;
		}
		Recipe best = null;
		for (Recipe recipe : RECIPES) {
			if (recipe.ingredients.equals(inputs)) {
				if (best == null || recipe.totalIngredients > best.totalIngredients) {
					best = recipe;
				}
			}
		}
		return best;
	}
}
