package com.claude.automata.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link MachineBlock} with a horizontal facing, used by the Block Breaker and
 * Block Placer so they act on the block in the direction they face. Placed
 * facing the same way the player is looking.
 */
public abstract class FacingMachineBlock extends MachineBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	protected FacingMachineBlock(Properties settings) {
		super(settings);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
	}
}
