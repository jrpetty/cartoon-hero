package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A plinth that showcases a single item floating and slowly rotating above it.
 *
 * <p>Right-click with an item to place it; right-click again with an item to
 * cycle the display size; right-click empty-handed to cycle the spin speed;
 * sneak + right-click empty-handed to take the item back.
 */
public class DisplayPedestalBlock extends Block implements BlockEntityProvider {
    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Slow", "Medium", "Fast"};

    public DisplayPedestalBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be.getDisplayed().isEmpty()) {
            if (!world.isClient()) {
                be.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
            }
            return ItemActionResult.SUCCESS;
        }
        // Pedestal already occupied: poking it with an item resizes the display.
        if (!world.isClient()) {
            int s = be.cycleScale();
            player.sendMessage(Text.literal("Display size: " + SCALE_NAMES[s]), true);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be) || be.getDisplayed().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            if (player.isSneaking()) {
                player.giveItemStack(be.removeDisplayed());
            } else {
                int s = be.cycleSpin();
                player.sendMessage(Text.literal("Spin speed: " + SPIN_NAMES[s]), true);
            }
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be && !be.getDisplayed().isEmpty()) {
                Block.dropStack(world, pos, be.getDisplayed());
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
