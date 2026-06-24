package com.claude.automata.block;

import com.claude.automata.block.entity.ConduitBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Power Conduit block — a slim cable that relays energy between non-adjacent
 * blocks. Right-clicking reports its stored energy (via {@link EnergyBlock}).
 */
public class ConduitBlock extends EnergyBlock {
	public static final MapCodec<ConduitBlock> CODEC = createCodec(ConduitBlock::new);
	private static final VoxelShape SHAPE = createCuboidShape(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

	public ConduitBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<ConduitBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ConduitBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.CONDUIT, (w, p, s, be) -> be.tick(w, p, s));
	}
}
