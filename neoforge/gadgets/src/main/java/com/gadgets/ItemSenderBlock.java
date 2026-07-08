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
 * Pulls items from the inventory directly above it and teleports them to an
 * Item Receiver on the same channel. Tune it with the Redstone Linker.
 */
public class ItemSenderBlock extends Block implements EntityBlock {
    public ItemSenderBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemSenderBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.ITEM_SENDER_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<ItemSenderBlockEntity>) ItemSenderBlockEntity::tick
                : null;
    }
}
