package com.claude.automata.recipe;

import com.claude.automata.block.entity.GrindingMachineBlockEntity.ProcessResult;
import com.claude.automata.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * The Recycler's table: melt unwanted iron/gold gear back into nuggets — at a
 * loss versus the ingots that went in, but better than nothing.
 */
public final class RecyclingRecipes {
	private RecyclingRecipes() {
	}

	private static final Map<Item, ProcessResult> RESULTS = new HashMap<>();

	private static void add(Item input, Item output, int count) {
		RESULTS.put(input, new ProcessResult(output, count));
	}

	static {
		// Iron gear -> iron nuggets.
		add(Items.IRON_SWORD, Items.IRON_NUGGET, 2);
		add(Items.IRON_PICKAXE, Items.IRON_NUGGET, 3);
		add(Items.IRON_AXE, Items.IRON_NUGGET, 3);
		add(Items.IRON_SHOVEL, Items.IRON_NUGGET, 1);
		add(Items.IRON_HOE, Items.IRON_NUGGET, 2);
		add(Items.IRON_HELMET, Items.IRON_NUGGET, 4);
		add(Items.IRON_CHESTPLATE, Items.IRON_NUGGET, 6);
		add(Items.IRON_LEGGINGS, Items.IRON_NUGGET, 5);
		add(Items.IRON_BOOTS, Items.IRON_NUGGET, 3);
		add(Items.SHEARS, Items.IRON_NUGGET, 2);
		add(Items.CHAINMAIL_HELMET, Items.IRON_NUGGET, 2);
		add(Items.CHAINMAIL_CHESTPLATE, Items.IRON_NUGGET, 3);
		add(Items.CHAINMAIL_LEGGINGS, Items.IRON_NUGGET, 3);
		add(Items.CHAINMAIL_BOOTS, Items.IRON_NUGGET, 2);

		// Gold gear -> gold nuggets.
		add(Items.GOLDEN_SWORD, Items.GOLD_NUGGET, 2);
		add(Items.GOLDEN_PICKAXE, Items.GOLD_NUGGET, 3);
		add(Items.GOLDEN_AXE, Items.GOLD_NUGGET, 3);
		add(Items.GOLDEN_SHOVEL, Items.GOLD_NUGGET, 1);
		add(Items.GOLDEN_HOE, Items.GOLD_NUGGET, 2);
		add(Items.GOLDEN_HELMET, Items.GOLD_NUGGET, 4);
		add(Items.GOLDEN_CHESTPLATE, Items.GOLD_NUGGET, 6);
		add(Items.GOLDEN_LEGGINGS, Items.GOLD_NUGGET, 5);
		add(Items.GOLDEN_BOOTS, Items.GOLD_NUGGET, 3);

		// This mod's own tool.
		add(ModItems.PULSAR_MULTITOOL, Items.IRON_NUGGET, 6);
	}

	public static ProcessResult result(Item input) {
		return RESULTS.get(input);
	}
}
