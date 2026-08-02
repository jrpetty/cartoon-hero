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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wall panel that shows one line of a Command Hub's board. Link it with the
 * Monitor Wand, then right-click to choose which gadget it displays.
 */
public class CommandHubMonitorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CommandHubMonitorBlock> CODEC = simpleCodec(CommandHubMonitorBlock::new);

    /**
     * The 4-pixel slab the model occupies. It hangs off the wall behind the
     * panel — the side opposite FACING — so the shape is on that side too, not
     * the side the screen looks out of. Without a shape at all the panel kept a
     * full cube's outline and collision and you bumped into thin air in front
     * of it. Indexed by {@link Direction#get2DDataValue()}: south, west, north, east.
     */
    private static final VoxelShape[] PANEL = {
            Block.box(0, 0, 0, 16, 16, 4),   // facing south — mounted on the north wall
            Block.box(12, 0, 0, 16, 16, 16), // facing west  — mounted on the east wall
            Block.box(0, 0, 12, 16, 16, 16), // facing north — mounted on the south wall
            Block.box(0, 0, 0, 4, 16, 16),   // facing east  — mounted on the west wall
    };

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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PANEL[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Hang it off the wall that was actually clicked, so it sits flush
        // against that block and looks back out of it. Clicking a floor or a
        // ceiling gives no wall to hang from, so those fall back to simply
        // facing the player.
        Direction clicked = ctx.getClickedFace();
        Direction front = clicked.getAxis().isHorizontal()
                ? clicked
                : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, front);
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
