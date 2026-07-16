package com.gadgets;

import java.util.List;
import java.util.Map;

import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Counts items passing the block it points at and pulses redstone every N items.
 * The display face shows a live readout (items/min, items/hour, lifetime total,
 * or pulse progress — sneak + empty hand to choose). Right-click with an empty
 * hand for the full stats dashboard: rates, total with uptime, and the top item
 * types counted. Right-click with redstone dust to change the pulse size.
 */
public class ItemCounterBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;

    public ItemCounterBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Point the reading face into whatever surface the block was placed against.
        return getDefaultState().with(FACING, ctx.getSide().getOpposite());
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(Items.REDSTONE)) {
            if (!world.isClient() && world.getBlockEntity(pos) instanceof ItemCounterBlockEntity be) {
                int t = be.cycleThreshold();
                world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.6F, 1.3F);
                player.sendMessage(Text.literal("Item Counter ▸ pulse every " + t + (t == 1 ? " item" : " items"))
                        .formatted(Formatting.GOLD), true);
            }
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof ItemCounterBlockEntity be) {
            if (player.isSneaking()) {
                String label = be.cycleDisplayMode();
                world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.6F, 1.6F);
                player.sendMessage(Text.literal("Item Counter ▸ face shows " + label)
                        .formatted(Formatting.GOLD), true);
            } else {
                sendDashboard(player, be);
            }
        }
        return ActionResult.success(world.isClient());
    }

    /** The full stats readout, printed to chat — the counter's "screen". */
    private static void sendDashboard(PlayerEntity player, ItemCounterBlockEntity be) {
        String mode = be.isWatchingContainer() ? "watching container" : "watching for drops";
        player.sendMessage(Text.literal("⚙ Item Counter — " + mode).formatted(Formatting.GOLD, Formatting.BOLD), false);
        player.sendMessage(Text.literal(" Rate  " + ItemCounterBlockEntity.fmt(be.getRateMin()) + " /min · "
                + ItemCounterBlockEntity.fmt(be.getRateHour()) + " /hour").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal(" Total " + ItemCounterBlockEntity.fmt(be.getTotal()) + " items in "
                + ItemCounterBlockEntity.duration(be.getUptimeTicks())).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal(" Pulse every " + be.getThreshold() + " items · at " + be.getCount()
                + " (redstone dust to change)").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal(" Face  showing " + be.faceLabel() + " (sneak + click to change)")
                .formatted(Formatting.AQUA), false);
        List<Map.Entry<String, Long>> top = be.topItems(5);
        if (!top.isEmpty()) {
            player.sendMessage(Text.literal(" Top items").formatted(Formatting.GOLD), false);
            for (Map.Entry<String, Long> e : top) {
                player.sendMessage(Text.literal("  • " + ItemCounterBlockEntity.displayName(e.getKey())
                        + " × " + ItemCounterBlockEntity.fmt(e.getValue())).formatted(Formatting.GRAY), false);
            }
        }
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ItemCounterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.ITEM_COUNTER_BE);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<ItemCounterBlockEntity> expected) {
        return given == expected
                ? (BlockEntityTicker<T>) (BlockEntityTicker<ItemCounterBlockEntity>) ItemCounterBlockEntity::tick
                : null;
    }
}
