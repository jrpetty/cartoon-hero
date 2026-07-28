package com.gadgets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the redstone power fed into it and broadcasts that level on its channel.
 * Link it to a receiver with the Redstone Linker.
 */
public class RedstoneTransmitterBlock extends Block implements EntityBlock {
    public RedstoneTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Never swallow the Linker's click — it has to reach the item.
        return stack.getItem() instanceof RedstoneLinkerItem
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RedstoneTransmitterBlockEntity be) {
            String channel = be.getChannel();
            int power = level.getBestNeighborSignal(pos);
            player.displayClientMessage(channel.isEmpty()
                    ? Component.literal("Transmitter ▸ no channel — right-click it with a Redstone Linker").withStyle(ChatFormatting.RED)
                    : Component.literal("Transmitter ▸ " + channel + " · broadcasting " + power + "/15")
                            .withStyle(power > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneTransmitterBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.REDSTONE_TRANSMITTER_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<RedstoneTransmitterBlockEntity>) RedstoneTransmitterBlockEntity::tick
                : null;
    }
}
