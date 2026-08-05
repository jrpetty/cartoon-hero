package com.gadgets;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Command Hub console. Its screen faces the player; link gadgets to it
 * with the Monitor Wand. Right-click opens the monitoring board.
 *
 * <p>Emits redstone whenever a linked monitor goes low or a linked counter
 * stalls, so one hub can drive a single base-wide alarm instead of wiring
 * every gadget's own signal separately.
 */
public class CommandHubBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CommandHubBlock> CODEC = simpleCodec(CommandHubBlock::new);
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

    public CommandHubBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(ALARM, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ALARM);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ALARM) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Screen toward the player who placed it.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof CommandHubBlockEntity be) {
            ScreenOpener.HUB.accept(be);
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CommandHubBlockEntity be && player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("Command Hub ▸ " + be.nodeCount() + "/" + CommandHubBlockEntity.MAX_NODES
                    + " linked · " + ItemCounterBlockEntity.fmt(be.totalRateMin()) + "/min · "
                    + be.alarmCount() + " alerts").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CommandHubBlockEntity(pos, state);
    }

    /**
     * Stop answering tablets once the hub is genuinely gone.
     *
     * <p>Deliberately here and not in the block entity's own removal: that runs
     * on chunk unload too, and a hub going quiet because you walked away is the
     * exact case a tablet exists to report on. Only breaking the block should
     * turn "last heard from 4m ago" into "nothing answering that code".
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CommandHubBlockEntity be) {
                HubRegistry.forget(be.getCode(), pos.asLong());
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.COMMAND_HUB_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<CommandHubBlockEntity>) CommandHubBlockEntity::tick
                : null;
    }
}
