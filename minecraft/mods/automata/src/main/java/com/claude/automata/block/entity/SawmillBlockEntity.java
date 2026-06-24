package com.claude.automata.block.entity;

import com.claude.automata.recipe.SawmillRecipes;
import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

/**
 * The Sawmill — turns one log into six planks. Runs slowly by hand, fast with
 * adjacent power.
 */
public class SawmillBlockEntity extends GrindingMachineBlockEntity {
	private static final int SAW_TICKS = 120;

	public SawmillBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SAWMILL, pos, state);
	}

	@Override
	protected int baseTicks() {
		return SAW_TICKS;
	}

	@Override
	protected ProcessResult process(Item input) {
		return SawmillRecipes.result(input);
	}
}
