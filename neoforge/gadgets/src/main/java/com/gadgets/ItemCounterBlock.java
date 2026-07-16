package com.gadgets;

import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import org.jetbrains.annotations.Nullable;

/**
 * Counts items passing the block it points at and pulses redstone every N items.
 * The display face shows a live readout (items/min, items/hour, lifetime total,
 * or pulse progress — sneak + empty hand to choose). Right-click with an empty
 * hand for the full stats dashboard: rates, total with uptime, and the top item
 * types counted. Right-click with redstone dust to change the pulse size.
 */
public class ItemCounterBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ItemCounterBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Point the reading face into whatever surface the block was placed against.
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.REDSTONE)) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ItemCounterBlockEntity be) {
                int t = be.cycleThreshold();
                level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6F, 1.3F);
                player.displayClientMessage(Component.literal("Item Counter ▸ pulse every " + t + (t == 1 ? " item" : " items"))
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ItemCounterBlockEntity be) {
            if (player.isShiftKeyDown()) {
                String label = be.cycleDisplayMode();
                level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6F, 1.6F);
                player.displayClientMessage(Component.literal("Item Counter ▸ face shows " + label)
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                sendDashboard(player, be);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** The full stats readout, printed to chat — the counter's "screen". */
    private static void sendDashboard(Player player, ItemCounterBlockEntity be) {
        String mode = be.isWatchingContainer() ? "watching container" : "watching for drops";
        player.displayClientMessage(Component.literal("⚙ Item Counter — " + mode)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.literal(" Rate  " + ItemCounterBlockEntity.fmt(be.getRateMin()) + " /min · "
                + ItemCounterBlockEntity.fmt(be.getRateHour()) + " /hour").withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(" Total " + ItemCounterBlockEntity.fmt(be.getTotal()) + " items in "
                + ItemCounterBlockEntity.duration(be.getUptimeTicks())).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(" Pulse every " + be.getThreshold() + " items · at " + be.getCount()
                + " (redstone dust to change)").withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(" Face  showing " + be.faceLabel() + " (sneak + click to change)")
                .withStyle(ChatFormatting.AQUA), false);
        List<Map.Entry<String, Long>> top = be.topItems(5);
        if (!top.isEmpty()) {
            player.displayClientMessage(Component.literal(" Top items").withStyle(ChatFormatting.GOLD), false);
            for (Map.Entry<String, Long> e : top) {
                player.displayClientMessage(Component.literal("  • " + ItemCounterBlockEntity.displayName(e.getKey())
                        + " × " + ItemCounterBlockEntity.fmt(e.getValue())).withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemCounterBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == Gadgets.ITEM_COUNTER_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<ItemCounterBlockEntity>) ItemCounterBlockEntity::tick
                : null;
    }
}
