package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A plinth that showcases a single item floating and slowly rotating above it.
 *
 * <p>Right-click with an item to place it; right-click again with an item to
 * cycle the display size; right-click empty-handed to cycle the spin speed;
 * sneak + right-click empty-handed to take the item back.
 */
public class DisplayPedestalBlock extends Block implements EntityBlock {
    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Slow", "Medium", "Fast"};

    public DisplayPedestalBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be.getDisplayed().isEmpty()) {
            if (!level.isClientSide()) {
                be.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Pedestal already occupied: poking it with an item resizes the display.
        if (!level.isClientSide()) {
            int s = be.cycleScale();
            player.displayClientMessage(Component.literal("Display size: " + SCALE_NAMES[s]), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be) || be.getDisplayed().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                ItemStack taken = be.removeDisplayed();
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
            } else {
                int s = be.cycleSpin();
                player.displayClientMessage(Component.literal("Spin speed: " + SPIN_NAMES[s]), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be && !be.getDisplayed().isEmpty()) {
                Block.popResource(level, pos, be.getDisplayed());
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
