package com.claude.automata.block;

import com.claude.automata.block.entity.ConduitBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Power Conduit block — a slim cable that relays energy between non-adjacent
 * blocks. Right-clicking reports its stored energy (via {@link EnergyBlock}).
 */
public class ConduitBlock extends EnergyBlock {
	public static final MapCodec<ConduitBlock> CODEC = simpleCodec(ConduitBlock::new);
	private static final VoxelShape SHAPE = box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

	public ConduitBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<ConduitBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ConduitBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.CONDUIT.get(), (w, p, s, be) -> be.tick(w, p, s));
	}
}
