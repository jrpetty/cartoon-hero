package com.claude.automata.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link MachineBlock} with a horizontal facing, used by the Block Breaker and
 * Block Placer so they act on the block in the direction they face. Placed
 * facing the same way the player is looking.
 */
public abstract class FacingMachineBlock extends MachineBlock {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	protected FacingMachineBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}
}
