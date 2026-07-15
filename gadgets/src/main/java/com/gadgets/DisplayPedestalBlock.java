package com.gadgets;

import net.minecraft.world.WorldAccess;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.StateManager;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A glass display case that showcases a single item floating inside it.
 *
 * <ul>
 *   <li>Right-click with an item (case empty) → put it on display.</li>
 *   <li>Right-click the <b>top</b> with an item (case occupied) → cycle spin:
 *       Off → Slow → Medium → Fast.</li>
 *   <li>Right-click a <b>side</b> with an item (case occupied) → cycle size.</li>
 *   <li>Right-click with an <b>empty hand</b> → take the item back.</li>
 *   <li>It is also a one-slot inventory: hoppers and Item Receivers can load it.</li>
 * </ul>
 */
public class DisplayPedestalBlock extends Block implements BlockEntityProvider {
    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Off", "Slow", "Medium", "Fast"};

    public static final BooleanProperty HAS_UP = BooleanProperty.of("has_up");
    public static final BooleanProperty HAS_DOWN = BooleanProperty.of("has_down");

    public DisplayPedestalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(HAS_UP, false).with(HAS_DOWN, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HAS_UP, HAS_DOWN);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        return getDefaultState()
                .with(HAS_UP, world.getBlockState(pos.up()).getBlock() instanceof DisplayPedestalBlock)
                .with(HAS_DOWN, world.getBlockState(pos.down()).getBlock() instanceof DisplayPedestalBlock);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.with(HAS_UP, neighborState.getBlock() instanceof DisplayPedestalBlock);
        }
        if (direction == Direction.DOWN) {
            return state.with(HAS_DOWN, neighborState.getBlock() instanceof DisplayPedestalBlock);
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    /** The bottom-most pedestal of a stacked column owns the displayed item. */
    @Nullable
    private DisplayPedestalBlockEntity owner(World world, BlockPos pos) {
        BlockPos.Mutable p = pos.mutableCopy();
        for (int i = 0; i < 8 && world.getBlockState(p.down()).getBlock() instanceof DisplayPedestalBlock; i++) {
            p.move(Direction.DOWN);
        }
        return world.getBlockEntity(p) instanceof DisplayPedestalBlockEntity be ? be : null;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        DisplayPedestalBlockEntity be = owner(world, pos);
        if (be == null) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be.getDisplayed().isEmpty()) {
            if (!world.isClient()) {
                be.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
            }
            return ItemActionResult.SUCCESS;
        }
        // Occupied: top face tunes the spin, sides tune the size.
        if (!world.isClient()) {
            if (hit.getSide() == Direction.UP) {
                int s = be.cycleSpin();
                player.sendMessage(Text.literal("Spin: " + SPIN_NAMES[s]).formatted(Formatting.GOLD), true);
            } else {
                int s = be.cycleScale();
                player.sendMessage(Text.literal("Display size: " + SCALE_NAMES[s]).formatted(Formatting.GOLD), true);
            }
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        DisplayPedestalBlockEntity be = owner(world, pos);
        if (be == null || be.getDisplayed().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            player.giveItemStack(be.removeDisplayed());
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be && !be.getDisplayed().isEmpty()) {
                Block.dropStack(world, pos, be.getDisplayed());
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
