package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Rings when a chosen target enters range. Tune with a spawn egg (specific mob)
 * or an empty hand (cycle players → monsters → animals → all).
 */
public class AlarmBlock extends Block implements EntityBlock {
    public AlarmBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlarmBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.ALARM_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<AlarmBlockEntity>) AlarmBlockEntity::tick
                : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof SpawnEggItem) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
                String eggId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                String mobId = eggId.endsWith("_spawn_egg")
                        ? eggId.substring(0, eggId.length() - "_spawn_egg".length())
                        : eggId;
                be.setTarget(mobId);
                player.displayClientMessage(Component.literal("Alarm now watches for: " + mobId), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            String mode = be.cycleMode();
            player.displayClientMessage(Component.literal("Alarm now watches for: " + mode), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
