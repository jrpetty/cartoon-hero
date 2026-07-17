package com.gadgets;

import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A filtered void bin. Pipe or hopper items into it and they are destroyed.
 * Right-click with an item to restrict it to voiding only that item;
 * right-click empty-handed to clear the filter (voids everything again).
 */
public class TrashCanBlock extends Block implements BlockEntityProvider {
    public TrashCanBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrashCanBlockEntity(pos, state);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof TrashCanBlockEntity be) {
            be.setFilter(stack.getItem());
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.6F, 1.3F);
            player.sendMessage(Text.literal("Trash Can ▸ voiding only ").formatted(Formatting.GOLD)
                    .append(stack.getName()), true);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof TrashCanBlockEntity be) {
            be.setFilter(Items.AIR);
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.6F, 1.0F);
            player.sendMessage(Text.literal("Trash Can ▸ voiding everything").formatted(Formatting.GOLD), true);
        }
        return ActionResult.success(world.isClient());
    }
}
