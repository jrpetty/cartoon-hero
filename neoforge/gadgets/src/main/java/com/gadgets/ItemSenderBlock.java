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
 * Pulls items from the inventory directly above it and teleports them to an
 * Item Receiver on the same channel. Tune it with the Redstone Linker.
 */
public class ItemSenderBlock extends Block implements EntityBlock {
    public ItemSenderBlock(Properties properties) {
        super(properties);
    }

    /**
     * The stepped funnel the model draws: a small foot, a slim body, then the
     * collar and mouth flaring out at the top. Worth spelling out rather than
     * boxing the whole cube, because the flare at the top is the whole cue for
     * which way the block faces and the outline should show it.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(4, 2, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 12, 14),
            Block.box(1, 12, 1, 15, 16, 15));

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof ItemSenderBlockEntity be) {
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
