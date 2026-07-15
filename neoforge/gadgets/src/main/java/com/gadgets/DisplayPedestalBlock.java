package com.gadgets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A base that showcases an item floating above it — and whose own look you can
 * change by placing a block onto it (like the Drain's disguise).
 *
 * <ul>
 *   <li>Right-click with a <b>block</b> → re-skin the base to look like it.</li>
 *   <li>Right-click with a <b>non-block item</b> (group empty) → set the floating showcase.</li>
 *   <li>Right-click a showcased pedestal — top with an item cycles spin, a side cycles size.</li>
 *   <li>Right-click empty-handed → take the showcased item back.</li>
 *   <li>Sneak + right-click empty-handed → pop the skin back off.</li>
 * </ul>
 *
 * <p>Place bases side by side (a filled rectangle up to 3×3) and they share one
 * showcase, centred over the whole platform.
 */
public class DisplayPedestalBlock extends Block implements EntityBlock {
    public static final BooleanProperty DISGUISED = BooleanProperty.create("disguised");

    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Off", "Slow", "Medium", "Fast"};

    public DisplayPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(DISGUISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISGUISED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    /**
     * The bounds {@code {minX, minZ, maxX, maxZ}} of the filled-rectangle group of
     * connected pedestals (at most 3×3) that {@code pos} belongs to. Anything
     * bigger or non-rectangular collapses to just this block (a 1×1 group).
     */
    public static int[] groupBounds(BlockGetter level, BlockPos pos) {
        int minX = pos.getX(), maxX = pos.getX(), minZ = pos.getZ(), maxZ = pos.getZ();
        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> stack = new ArrayDeque<>();
        stack.push(pos);
        seen.add(pos.asLong());
        int count = 0;
        while (!stack.isEmpty()) {
            BlockPos p = stack.pop();
            count++;
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            if (maxX - minX > 2 || maxZ - minZ > 2) {
                return solo(pos);
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.add(n.asLong()) && level.getBlockState(n).getBlock() instanceof DisplayPedestalBlock) {
                    stack.push(n);
                }
            }
        }
        int w = maxX - minX + 1, dp = maxZ - minZ + 1;
        return count == w * dp ? new int[]{minX, minZ, maxX, maxZ} : solo(pos);
    }

    private static int[] solo(BlockPos pos) {
        return new int[]{pos.getX(), pos.getZ(), pos.getX(), pos.getZ()};
    }

    /** The one pedestal in {@code pos}'s group that currently holds a showcase item, or null. */
    @Nullable
    public static DisplayPedestalBlockEntity holder(BlockGetter level, BlockPos pos) {
        int[] g = groupBounds(level, pos);
        int y = pos.getY();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = g[0]; x <= g[2]; x++) {
            for (int z = g[1]; z <= g[3]; z++) {
                if (level.getBlockEntity(p.set(x, y, z)) instanceof DisplayPedestalBlockEntity be
                        && !be.getDisplayed().isEmpty()) {
                    return be;
                }
            }
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity clicked)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A block re-skins the base.
        if (stack.getItem() instanceof BlockItem) {
            if (!level.isClientSide()) {
                ItemStack old = clicked.getDisguise();
                clicked.setDisguise(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (!old.isEmpty() && !player.getInventory().add(old)) {
                    player.drop(old, false);
                }
                player.displayClientMessage(Component.literal("Pedestal skinned as " + clicked.getDisguise().getHoverName().getString())
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // A non-block item is the floating showcase.
        DisplayPedestalBlockEntity held = holder(level, pos);
        if (held == null) {
            if (!level.isClientSide()) {
                clicked.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            if (hit.getDirection() == Direction.UP) {
                int s = held.cycleSpin();
                player.displayClientMessage(Component.literal("Spin: " + SPIN_NAMES[s]).withStyle(ChatFormatting.GOLD), true);
            } else {
                int s = held.cycleScale();
                player.displayClientMessage(Component.literal("Display size: " + SCALE_NAMES[s]).withStyle(ChatFormatting.GOLD), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity clicked)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (clicked.getDisguise().isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                ItemStack skin = clicked.removeDisguise();
                if (!player.getInventory().add(skin)) {
                    player.drop(skin, false);
                }
                player.displayClientMessage(Component.literal("Skin removed").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        DisplayPedestalBlockEntity held = holder(level, pos);
        if (held == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = held.removeDisplayed();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be) {
                if (!be.getDisplayed().isEmpty()) {
                    Block.popResource(level, pos, be.getDisplayed());
                }
                if (!be.getDisguise().isEmpty()) {
                    Block.popResource(level, pos, be.getDisguise());
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
