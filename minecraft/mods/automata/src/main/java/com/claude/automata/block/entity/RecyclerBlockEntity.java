package com.claude.automata.block.entity;

import com.claude.automata.recipe.RecyclingRecipes;
import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

/**
 * The Recycler — melts unwanted iron/gold gear back into nuggets (at a loss).
 * Runs slowly by hand, fast with adjacent power.
 */
public class RecyclerBlockEntity extends GrindingMachineBlockEntity {
	private static final int RECYCLE_TICKS = 160;

	public RecyclerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RECYCLER, pos, state);
	}

	@Override
	protected int baseTicks() {
		return RECYCLE_TICKS;
	}

	@Override
	protected ProcessResult process(Item input) {
		return RecyclingRecipes.result(input);
	}
}
