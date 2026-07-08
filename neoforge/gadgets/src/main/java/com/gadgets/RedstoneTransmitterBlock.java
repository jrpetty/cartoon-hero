package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the redstone power fed into it and broadcasts that level on its channel.
 * Link it to a receiver with the Redstone Linker.
 */
public class RedstoneTransmitterBlock extends Block implements EntityBlock {
    public RedstoneTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneTransmitterBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.REDSTONE_TRANSMITTER_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<RedstoneTransmitterBlockEntity>) RedstoneTransmitterBlockEntity::tick
                : null;
    }
}
