package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Receives teleported items on its channel and deposits them into the inventory
 * directly below it. Tune it with the Redstone Linker.
 */
public class ItemReceiverBlock extends Block implements EntityBlock {
    public ItemReceiverBlock(Properties properties) {
        super(properties);
    }

    /**
     * The same stepped funnel as the sender's, stood on its head: the mouth
     * flares out at the bottom, where the items drop through.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(2, 4, 2, 14, 6, 14),
            Block.box(4, 6, 4, 12, 14, 12),
            Block.box(3, 14, 3, 13, 16, 13));

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof ItemReceiverBlockEntity be) {
            ScreenOpener.TRANSFER.accept(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
