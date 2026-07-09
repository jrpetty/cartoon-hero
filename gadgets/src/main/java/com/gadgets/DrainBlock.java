package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A grate you can walk on that swallows any fluid landing on top of it.
 *
 * <p>It can also be disguised: right-click it with any block (or pipe one into
 * its single-slot inventory) and the drain renders as that block — a stone
 * floor stays looking like stone while quietly draining. Right-click with an
 * empty hand to take the disguise back.
 */
public class DrainBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty DISGUISED = BooleanProperty.of("disguised");

    public DrainBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(DISGUISED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(DISGUISED);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DrainBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.DRAIN_BE);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem)
                || !(world.getBlockEntity(pos) instanceof DrainBlockEntity be)
                || !be.getDisguise().isEmpty()) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!world.isClient()) {
            be.setDisguise(stack.copyWithCount(1));
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            player.sendMessage(Text.literal("Drain disguised as " + stack.getName().getString()), true);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DrainBlockEntity be) || be.getDisguise().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            player.giveItemStack(be.removeDisguise());
            player.sendMessage(Text.literal("Disguise removed"), true);
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof DrainBlockEntity be && !be.getDisguise().isEmpty()) {
                Block.dropStack(world, pos, be.getDisguise());
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<DrainBlockEntity> expected) {
        return given == expected
                ? (BlockEntityTicker<T>) (BlockEntityTicker<DrainBlockEntity>) DrainBlockEntity::tick
                : null;
    }
}
