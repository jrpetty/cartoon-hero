package com.claude.automata.recipe;

import com.claude.automata.block.entity.GrindingMachineBlockEntity.ProcessResult;
import com.claude.automata.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * The Crusher's recipe table: ores and raw metals crush into two dust (which a
 * Forge Core then smelts back into an ingot — net ore doubling), plus a few
 * earth conversions.
 */
public final class CrushingRecipes {
	private CrushingRecipes() {
	}

	private static final Map<Item, ProcessResult> RESULTS = new HashMap<>();

	private static void add(Item input, Item output, int count) {
		RESULTS.put(input, new ProcessResult(output, count));
	}

	static {
		// Ore doubling: raw metals and ore blocks -> 2 dust.
		add(Items.RAW_IRON, ModItems.IRON_DUST, 2);
		add(Items.IRON_ORE, ModItems.IRON_DUST, 2);
		add(Items.DEEPSLATE_IRON_ORE, ModItems.IRON_DUST, 2);
		add(Items.RAW_GOLD, ModItems.GOLD_DUST, 2);
		add(Items.GOLD_ORE, ModItems.GOLD_DUST, 2);
		add(Items.DEEPSLATE_GOLD_ORE, ModItems.GOLD_DUST, 2);
		add(Items.NETHER_GOLD_ORE, ModItems.GOLD_DUST, 2);
		add(Items.RAW_COPPER, ModItems.COPPER_DUST, 2);
		add(Items.COPPER_ORE, ModItems.COPPER_DUST, 2);
		add(Items.DEEPSLATE_COPPER_ORE, ModItems.COPPER_DUST, 2);

		// Earth conversions.
		add(Items.COBBLESTONE, Items.GRAVEL, 1);
		add(Items.GRAVEL, Items.SAND, 1);
		add(Items.STONE, Items.COBBLESTONE, 1);

		// Bonus yields from grinding.
		add(Items.BLAZE_ROD, Items.BLAZE_POWDER, 4);
		add(Items.BONE, Items.BONE_MEAL, 4);
	}

	public static ProcessResult result(Item input) {
		return RESULTS.get(input);
	}
}
