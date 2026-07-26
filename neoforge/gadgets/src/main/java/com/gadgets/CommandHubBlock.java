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
 * The Command Hub console. Its screen faces the player; link gadgets to it
 * with the Monitor Wand. Right-click opens the monitoring board.
 */
public class CommandHubBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CommandHubBlock> CODEC = simpleCodec(CommandHubBlock::new);

    public CommandHubBlock(Properties properties) {
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
                    + be.lowCount() + " low").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CommandHubBlockEntity(pos, state);
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
