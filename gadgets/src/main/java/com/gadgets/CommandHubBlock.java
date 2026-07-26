package com.gadgets;

import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Command Hub console. Its screen faces the player; link gadgets to it
 * with the Monitor Wand. Right-click opens the monitoring board.
 */
public class CommandHubBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final com.mojang.serialization.MapCodec<CommandHubBlock> CODEC = createCodec(CommandHubBlock::new);

    public CommandHubBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Screen toward the player who placed it.
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient() && world.getBlockEntity(pos) instanceof CommandHubBlockEntity be) {
            ScreenOpener.HUB.accept(be);
        }
        if (!world.isClient() && world.getBlockEntity(pos) instanceof CommandHubBlockEntity be && player.isSneaking()) {
            player.sendMessage(Text.literal("Command Hub ▸ " + be.nodeCount() + "/" + CommandHubBlockEntity.MAX_NODES
                    + " linked · " + ItemCounterBlockEntity.fmt(be.totalRateMin()) + "/min · "
                    + be.lowCount() + " low").formatted(Formatting.GOLD), true);
        }
        return ActionResult.success(world.isClient());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CommandHubBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.COMMAND_HUB_BE);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<CommandHubBlockEntity> expected) {
        return given == expected
                ? (BlockEntityTicker<T>) (BlockEntityTicker<CommandHubBlockEntity>) CommandHubBlockEntity::tick
                : null;
    }
}
