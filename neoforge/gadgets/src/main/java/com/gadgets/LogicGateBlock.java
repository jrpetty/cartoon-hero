package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Wireless logic gate. Empty-hand right-click cycles the gate type; use the
 * Redstone Linker to bind its two input channels (sneak-paste) and copy its
 * output channel (right-click).
 */
public class LogicGateBlock extends Block implements EntityBlock {
    public LogicGateBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicGateBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.LOGIC_GATE_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<LogicGateBlockEntity>) LogicGateBlockEntity::tick
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LogicGateBlockEntity be) {
            String t = be.cycleType();
            player.displayClientMessage(Component.literal("Gate: " + t), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
