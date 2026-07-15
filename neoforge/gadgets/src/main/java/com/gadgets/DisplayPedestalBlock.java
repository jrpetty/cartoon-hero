package com.gadgets;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
public class DisplayPedestalBlock extends Block implements EntityBlock {
    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Off", "Slow", "Medium", "Fast"};

    public static final BooleanProperty HAS_UP = BooleanProperty.create("has_up");
    public static final BooleanProperty HAS_DOWN = BooleanProperty.create("has_down");

    public DisplayPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(HAS_UP, false).setValue(HAS_DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_UP, HAS_DOWN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        return defaultBlockState()
                .setValue(HAS_UP, level.getBlockState(pos.above()).getBlock() instanceof DisplayPedestalBlock)
                .setValue(HAS_DOWN, level.getBlockState(pos.below()).getBlock() instanceof DisplayPedestalBlock);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.setValue(HAS_UP, neighborState.getBlock() instanceof DisplayPedestalBlock);
        }
        if (direction == Direction.DOWN) {
            return state.setValue(HAS_DOWN, neighborState.getBlock() instanceof DisplayPedestalBlock);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** The bottom-most pedestal of a stacked column owns the displayed item. */
    @Nullable
    private DisplayPedestalBlockEntity owner(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 8 && level.getBlockState(p.below()).getBlock() instanceof DisplayPedestalBlock; i++) {
            p.move(Direction.DOWN);
        }
        return level.getBlockEntity(p) instanceof DisplayPedestalBlockEntity be ? be : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        DisplayPedestalBlockEntity be = owner(level, pos);
        if (be == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be.getDisplayed().isEmpty()) {
            if (!level.isClientSide()) {
                be.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Occupied: top face tunes the spin, sides tune the size.
        if (!level.isClientSide()) {
            if (hit.getDirection() == Direction.UP) {
                int s = be.cycleSpin();
                player.displayClientMessage(Component.literal("Spin: " + SPIN_NAMES[s]).withStyle(ChatFormatting.GOLD), true);
            } else {
                int s = be.cycleScale();
                player.displayClientMessage(Component.literal("Display size: " + SCALE_NAMES[s]).withStyle(ChatFormatting.GOLD), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        DisplayPedestalBlockEntity be = owner(level, pos);
        if (be == null || be.getDisplayed().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = be.removeDisplayed();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be && !be.getDisplayed().isEmpty()) {
                Block.popResource(level, pos, be.getDisplayed());
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
