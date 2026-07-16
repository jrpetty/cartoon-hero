package com.gadgets;

import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Counts items passing the block it points at and pulses redstone every N items.
 * Point the reading face at a container (hopper, chest, Create depot/vault…) to
 * count items flowing out of it, or at open space where items drop (belt ends,
 * chutes, thrown items) to count what falls past. Right-click with an empty hand
 * to change the pulse size (1 → 4 → 8 → 16 → 32 → 64) and restart the count.
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
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof ItemCounterBlockEntity be) {
            if (player.isSneaking()) {
                String mode = be.isWatchingContainer() ? "watching container" : "watching for drops";
                player.sendMessage(Text.literal("Item Counter ▸ " + be.getCount() + " / " + be.getThreshold()
                        + " · " + mode).formatted(Formatting.AQUA), true);
            } else {
                int t = be.cycleThreshold();
                world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.6F, 1.3F);
                player.sendMessage(Text.literal("Item Counter ▸ pulse every " + t + (t == 1 ? " item" : " items"))
                        .formatted(Formatting.GOLD), true);
            }
        }
        return ActionResult.success(world.isClient());
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
