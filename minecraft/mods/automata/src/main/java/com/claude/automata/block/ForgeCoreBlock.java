package com.claude.automata.block;

import com.claude.automata.block.entity.ForgeCoreBlockEntity;
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

public class ForgeCoreBlock extends MachineBlock implements BlockEntityProvider {
	public static final MapCodec<ForgeCoreBlock> CODEC = createCodec(ForgeCoreBlock::new);

	public ForgeCoreBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<ForgeCoreBlock> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ForgeCoreBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.FORGE_CORE, (w, p, s, be) -> be.tick(w, p, s));
	}
}
