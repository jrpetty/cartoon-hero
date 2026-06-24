package com.claude.automata.recipe;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * The Forge Core's smelting table.
 *
 * <p>A deliberately curated map of input item -> smelted result. Kept as plain
 * Java (rather than querying the vanilla recipe manager) so the machine's
 * behaviour is explicit and predictable.
 */
public final class SmeltingRecipes {
	private SmeltingRecipes() {
	}

	private static final Map<Item, Item> RESULTS = new HashMap<>();

	private static void add(Item input, Item output) {
		RESULTS.put(input, output);
	}

	static {
		// Crushed dust from the Crusher (completes the ore-doubling loop).
		add(com.claude.automata.registry.ModItems.IRON_DUST, Items.IRON_INGOT);
		add(com.claude.automata.registry.ModItems.GOLD_DUST, Items.GOLD_INGOT);
		add(com.claude.automata.registry.ModItems.COPPER_DUST, Items.COPPER_INGOT);

		// Ores and raw metals.
		add(Items.RAW_IRON, Items.IRON_INGOT);
		add(Items.IRON_ORE, Items.IRON_INGOT);
		add(Items.DEEPSLATE_IRON_ORE, Items.IRON_INGOT);
		add(Items.RAW_GOLD, Items.GOLD_INGOT);
		add(Items.GOLD_ORE, Items.GOLD_INGOT);
		add(Items.DEEPSLATE_GOLD_ORE, Items.GOLD_INGOT);
		add(Items.NETHER_GOLD_ORE, Items.GOLD_INGOT);
		add(Items.RAW_COPPER, Items.COPPER_INGOT);
		add(Items.COPPER_ORE, Items.COPPER_INGOT);
		add(Items.DEEPSLATE_COPPER_ORE, Items.COPPER_INGOT);
		add(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);

		// Stone and earth.
		add(Items.COBBLESTONE, Items.STONE);
		add(Items.STONE, Items.SMOOTH_STONE);
		add(Items.SAND, Items.GLASS);
		add(Items.RED_SAND, Items.GLASS);
		add(Items.CLAY_BALL, Items.BRICK);
		add(Items.NETHERRACK, Items.NETHER_BRICK);
		add(Items.COBBLED_DEEPSLATE, Items.DEEPSLATE);
		add(Items.WET_SPONGE, Items.SPONGE);
		add(Items.CACTUS, Items.GREEN_DYE);
		add(Items.SEA_PICKLE, Items.LIME_DYE);
		add(Items.CHORUS_FRUIT, Items.POPPED_CHORUS_FRUIT);

		// Charcoal from logs.
		add(Items.OAK_LOG, Items.CHARCOAL);
		add(Items.BIRCH_LOG, Items.CHARCOAL);
		add(Items.SPRUCE_LOG, Items.CHARCOAL);
		add(Items.JUNGLE_LOG, Items.CHARCOAL);
		add(Items.ACACIA_LOG, Items.CHARCOAL);
		add(Items.DARK_OAK_LOG, Items.CHARCOAL);
		add(Items.MANGROVE_LOG, Items.CHARCOAL);
		add(Items.CHERRY_LOG, Items.CHARCOAL);

		// Food.
		add(Items.BEEF, Items.COOKED_BEEF);
		add(Items.PORKCHOP, Items.COOKED_PORKCHOP);
		add(Items.CHICKEN, Items.COOKED_CHICKEN);
		add(Items.MUTTON, Items.COOKED_MUTTON);
		add(Items.RABBIT, Items.COOKED_RABBIT);
		add(Items.COD, Items.COOKED_COD);
		add(Items.SALMON, Items.COOKED_SALMON);
		add(Items.POTATO, Items.BAKED_POTATO);
		add(Items.KELP, Items.DRIED_KELP);
	}

	/** The smelted result for {@code item}, or null if it cannot be smelted. */
	public static Item result(Item item) {
		return RESULTS.get(item);
	}

	public static boolean canSmelt(Item item) {
		return RESULTS.containsKey(item);
	}
}
