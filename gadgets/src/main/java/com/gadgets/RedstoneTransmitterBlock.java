package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the redstone power fed into it and broadcasts that level on its channel.
 * Link it to a receiver with the Redstone Linker.
 */
public class RedstoneTransmitterBlock extends Block implements BlockEntityProvider {
    public RedstoneTransmitterBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Never swallow the Linker's click — it has to reach the item.
        return stack.getItem() instanceof RedstoneLinkerItem
                ? ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof RedstoneTransmitterBlockEntity be) {
            String channel = be.getChannel();
            int power = world.getReceivedRedstonePower(pos);
            player.sendMessage(channel.isEmpty()
                    ? Text.literal("Transmitter ▸ no channel — right-click it with a Redstone Linker").formatted(Formatting.RED)
                    : Text.literal("Transmitter ▸ " + channel + " · broadcasting " + power + "/15")
                            .formatted(power > 0 ? Formatting.GREEN : Formatting.GRAY), true);
        }
        return ActionResult.success(world.isClient());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneTransmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.REDSTONE_TRANSMITTER_BE);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<RedstoneTransmitterBlockEntity> expected) {
        return given == expected
                ? (BlockEntityTicker<T>) (BlockEntityTicker<RedstoneTransmitterBlockEntity>) RedstoneTransmitterBlockEntity::tick
                : null;
    }
}
