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
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A glass display case that showcases a single item floating inside it.
 *
 * <ul>
 *   <li>Right-click with an item (case empty) → put it on display.</li>
 *   <li>Right-click the <b>top</b> with an item (case occupied) → cycle spin:
 *       Off → Slow → Medium → Fast.</li>
 *   <li>Right-click a <b>side</b> with an item (case occupied) → cycle size.</li>
 *   <li>Right-click with an <b>empty hand</b> → take the item back.</li>
 *   <li>It is also a one-slot inventory: hoppers and Item Receivers can load it.</li>
 * </ul>
 */
public class DisplayPedestalBlock extends Block implements BlockEntityProvider {
    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Off", "Slow", "Medium", "Fast"};

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
        // Occupied: top face tunes the spin, sides tune the size.
        if (!world.isClient()) {
            if (hit.getSide() == Direction.UP) {
                int s = be.cycleSpin();
                player.sendMessage(Text.literal("Spin: " + SPIN_NAMES[s]), true);
            } else {
                int s = be.cycleScale();
                player.sendMessage(Text.literal("Display size: " + SCALE_NAMES[s]), true);
            }
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be) || be.getDisplayed().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            player.giveItemStack(be.removeDisplayed());
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
