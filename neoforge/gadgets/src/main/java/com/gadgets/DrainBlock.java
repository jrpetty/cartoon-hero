package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A grate you can walk on that swallows any fluid landing on top of it.
 *
 * <p>It can also be disguised: right-click it with any block (or pipe one into
 * its single-slot inventory) and the drain renders as that block — a stone
 * floor stays looking like stone while quietly draining. Right-click with an
 * empty hand to take the disguise back.
 */
public class DrainBlock extends Block implements EntityBlock {
    public static final BooleanProperty DISGUISED = BooleanProperty.create("disguised");

    public DrainBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(DISGUISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISGUISED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrainBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.DRAIN_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<DrainBlockEntity>) DrainBlockEntity::tick
                : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem)
                || !(level.getBlockEntity(pos) instanceof DrainBlockEntity be)
                || !be.getDisguise().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            be.setDisguise(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("Drain disguised as " + stack.getHoverName().getString()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrainBlockEntity be) || be.getDisguise().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = be.removeDisguise();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            player.displayClientMessage(Component.literal("Disguise removed"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DrainBlockEntity be && !be.getDisguise().isEmpty()) {
                Block.popResource(level, pos, be.getDisguise());
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
