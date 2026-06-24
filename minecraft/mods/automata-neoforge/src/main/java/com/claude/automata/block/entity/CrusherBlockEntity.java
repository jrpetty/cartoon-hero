package com.claude.automata.block.entity;

import com.claude.automata.recipe.CrushingRecipes;
import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Crusher — grinds ores and raw metals into two dust each (ore doubling
 * once the dust is smelted in a Forge Core). Runs slowly by hand, fast with
 * adjacent power.
 */
public class CrusherBlockEntity extends GrindingMachineBlockEntity {
	private static final int CRUSH_TICKS = 160;

	public CrusherBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRUSHER.get(), pos, state);
	}

	@Override
	protected int baseTicks() {
		return CRUSH_TICKS;
	}

	@Override
	protected ProcessResult process(Item input) {
		return CrushingRecipes.result(input);
	}
}
