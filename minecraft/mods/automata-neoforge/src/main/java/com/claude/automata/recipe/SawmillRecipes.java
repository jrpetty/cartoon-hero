package com.claude.automata.recipe;

import com.claude.automata.block.entity.GrindingMachineBlockEntity.ProcessResult;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The Sawmill's recipe table: a log yields six planks instead of the four you
 * get crafting by hand — wood efficiency for the cost of building (and ideally
 * powering) the machine.
 */
public final class SawmillRecipes {
	private SawmillRecipes() {
	}

	private static final Map<Item, ProcessResult> RESULTS = new HashMap<>();

	private static void add(Item log, Item planks) {
		RESULTS.put(log, new ProcessResult(planks, 6));
	}

	static {
		add(Items.OAK_LOG, Items.OAK_PLANKS);
		add(Items.BIRCH_LOG, Items.BIRCH_PLANKS);
		add(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS);
		add(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS);
		add(Items.ACACIA_LOG, Items.ACACIA_PLANKS);
		add(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS);
		add(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS);
		add(Items.CHERRY_LOG, Items.CHERRY_PLANKS);
	}

	public static ProcessResult result(Item input) {
		return RESULTS.get(input);
	}
}
