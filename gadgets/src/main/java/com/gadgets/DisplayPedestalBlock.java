package com.gadgets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
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
public class DisplayPedestalBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty DISGUISED = BooleanProperty.of("disguised");

    private static final String[] SCALE_NAMES = {"Small", "Medium", "Large"};
    private static final String[] SPIN_NAMES = {"Off", "Slow", "Medium", "Fast"};

    public DisplayPedestalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(DISGUISED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(DISGUISED);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    /**
     * The bounds {@code {minX, minZ, maxX, maxZ}} of the filled-rectangle group of
     * connected pedestals (at most 3×3) that {@code pos} belongs to. Anything
     * bigger or non-rectangular collapses to just this block (a 1×1 group).
     */
    public static int[] groupBounds(BlockView world, BlockPos pos) {
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
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos n = p.offset(d);
                if (seen.add(n.asLong()) && world.getBlockState(n).getBlock() instanceof DisplayPedestalBlock) {
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
    public static DisplayPedestalBlockEntity holder(BlockView world, BlockPos pos) {
        int[] g = groupBounds(world, pos);
        int y = pos.getY();
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = g[0]; x <= g[2]; x++) {
            for (int z = g[1]; z <= g[3]; z++) {
                if (world.getBlockEntity(p.set(x, y, z)) instanceof DisplayPedestalBlockEntity be
                        && !be.getDisplayed().isEmpty()) {
                    return be;
                }
            }
        }
        return null;
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity clicked)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A block re-skins the base.
        if (stack.getItem() instanceof BlockItem) {
            if (!world.isClient()) {
                ItemStack old = clicked.getDisguise();
                clicked.setDisguise(stack.copyWithCount(1));
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                if (!old.isEmpty()) {
                    player.giveItemStack(old);
                }
                player.sendMessage(Text.literal("Pedestal skinned as " + clicked.getDisguise().getName().getString())
                        .formatted(Formatting.GREEN), true);
            }
            return ItemActionResult.SUCCESS;
        }
        // A non-block item is the floating showcase.
        DisplayPedestalBlockEntity held = holder(world, pos);
        if (held == null) {
            if (!world.isClient()) {
                clicked.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
            }
            return ItemActionResult.SUCCESS;
        }
        if (!world.isClient()) {
            if (hit.getSide() == Direction.UP) {
                int s = held.cycleSpin();
                player.sendMessage(Text.literal("Spin: " + SPIN_NAMES[s]).formatted(Formatting.GOLD), true);
            } else {
                int s = held.cycleScale();
                player.sendMessage(Text.literal("Display size: " + SCALE_NAMES[s]).formatted(Formatting.GOLD), true);
            }
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity clicked)) {
            return ActionResult.PASS;
        }
        if (player.isSneaking()) {
            if (clicked.getDisguise().isEmpty()) {
                return ActionResult.PASS;
            }
            if (!world.isClient()) {
                player.giveItemStack(clicked.removeDisguise());
                player.sendMessage(Text.literal("Skin removed").formatted(Formatting.GRAY), true);
            }
            return ActionResult.success(world.isClient());
        }
        DisplayPedestalBlockEntity held = holder(world, pos);
        if (held == null) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            player.giveItemStack(held.removeDisplayed());
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity be) {
                if (!be.getDisplayed().isEmpty()) {
                    Block.dropStack(world, pos, be.getDisplayed());
                }
                if (!be.getDisguise().isEmpty()) {
                    Block.dropStack(world, pos, be.getDisguise());
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
