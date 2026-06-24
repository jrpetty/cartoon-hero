package com.claude.automata.block;

import com.claude.automata.block.entity.VoidBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class VoidBlock extends MachineBlock implements BlockEntityProvider {
	public static final MapCodec<VoidBlock> CODEC = createCodec(VoidBlock::new);

	public VoidBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<VoidBlock> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new VoidBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.VOID, (w, p, s, be) -> be.tick(w, p, s));
	}
}
