package com.gadgets;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A wall panel that shows one line of a Command Hub's board. Link it with the
 * Monitor Wand, then right-click to choose which gadget it displays.
 */
public class CommandHubMonitorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CommandHubMonitorBlock> CODEC = simpleCodec(CommandHubMonitorBlock::new);

    public CommandHubMonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CommandHubMonitorBlockEntity be)) {
            return InteractionResult.PASS;
        }
        // Unlinked, there is nothing to choose from — say so instead of opening
        // an empty screen the player can't act on.
        if (!be.isHubLinked()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal(
                                "Monitor ▸ not linked — select a Command Hub with the Monitor Wand, then click this screen")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) {
            ScreenOpener.HUB_MONITOR.accept(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CommandHubMonitorBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.COMMAND_HUB_MONITOR_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<CommandHubMonitorBlockEntity>) CommandHubMonitorBlockEntity::tick
                : null;
    }
}
