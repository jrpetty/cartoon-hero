package com.claude.automata.recipe;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Fuel burn times (in ticks) for the Combustion Dynamo.
 *
 * <p>Kept as plain Java so the generator has no dependency on the vanilla fuel
 * registry. Values mirror vanilla furnace burn times for familiarity.
 */
public final class Fuels {
	private Fuels() {
	}

	private static final Map<Item, Integer> BURN_TICKS = new HashMap<>();

	private static void add(Item item, int ticks) {
		BURN_TICKS.put(item, ticks);
	}

	static {
		add(Items.COAL, 1600);
		add(Items.CHARCOAL, 1600);
		add(Items.COAL_BLOCK, 16000);
		add(Items.BLAZE_ROD, 2400);
		add(Items.DRIED_KELP_BLOCK, 4000);
		add(Items.STICK, 100);
		add(Items.BAMBOO, 50);

		// Planks and logs of every wood type.
		for (Item planks : new Item[]{Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.SPRUCE_PLANKS,
				Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
				Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS,
				Items.CRIMSON_PLANKS, Items.WARPED_PLANKS}) {
			add(planks, 300);
		}
		for (Item log : new Item[]{Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG,
				Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG,
				Items.CHERRY_LOG}) {
			add(log, 300);
		}
	}

	/** Burn time in ticks, or 0 if the item is not a fuel. */
	public static int burnTicks(Item item) {
		return BURN_TICKS.getOrDefault(item, 0);
	}

	public static boolean isFuel(Item item) {
		return BURN_TICKS.containsKey(item);
	}
}
