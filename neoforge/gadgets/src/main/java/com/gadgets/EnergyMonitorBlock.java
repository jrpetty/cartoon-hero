package com.gadgets;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Shows how full the energy store it faces is, and emits redstone while that
 * is below the alert level. Right-click to set the alert level and name it.
 */
public class EnergyMonitorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LOW = BooleanProperty.create("low");

    /** Sign-thin panel, flush against the face it reads. Indexed by Direction 3D data value. */
    private static final VoxelShape[] PANEL = {
            Block.box(0, 0, 0, 16, 2, 16),   // facing down
            Block.box(0, 14, 0, 16, 16, 16), // facing up
            Block.box(0, 0, 0, 16, 16, 2),   // facing north
            Block.box(0, 0, 14, 16, 16, 16), // facing south
            Block.box(0, 0, 0, 2, 16, 16),   // facing west
            Block.box(14, 0, 0, 16, 16, 16), // facing east
    };

    public EnergyMonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(LOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOW);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof EnergyMonitorBlockEntity be) {
            if (level.isClientSide()) {
                ScreenOpener.GAUGE.accept(be);
            } else if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal("Energy Monitor ▸ " + be.percent() + "% · " + be.amountText())
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PANEL[state.getValue(FACING).get3DDataValue()];
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(LOW) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyMonitorBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.ENERGY_MONITOR_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<EnergyMonitorBlockEntity>) EnergyMonitorBlockEntity::tick
                : null;
    }
}
