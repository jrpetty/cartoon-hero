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
 * Receives teleported items on its channel and deposits them into the inventory
 * directly below it. Tune it with the Redstone Linker.
 */
public class ItemReceiverBlock extends Block implements EntityBlock {
    public ItemReceiverBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemReceiverBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.ITEM_RECEIVER_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<ItemReceiverBlockEntity>) ItemReceiverBlockEntity::tick
                : null;
    }
}
