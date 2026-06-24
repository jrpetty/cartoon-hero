package com.claude.automata.block;

import com.claude.automata.block.entity.BlockPlacerBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

public class BlockPlacerBlock extends FacingMachineBlock implements EntityBlock {
	public static final MapCodec<BlockPlacerBlock> CODEC = simpleCodec(BlockPlacerBlock::new);

	public BlockPlacerBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<BlockPlacerBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BlockPlacerBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.BLOCK_PLACER.get(), (w, p, s, be) -> be.tick(w, p, s));
	}
}
