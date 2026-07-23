package com.jrpetty.aztecabyss.block;

import com.jrpetty.aztecabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Frame-detection and fill logic for the Abyss portal, mirroring vanilla's
 * nether portal shape algorithm but keyed to vanilla diamond/iron frame blocks
 * and filling with {@link ModBlocks#ABYSS_PORTAL}.
 *
 * Frame rules match a nether portal: a rectangular ring of diamond and/or iron
 * blocks, interior 2..21 wide and 3..21 tall, corners optional. The interior
 * may contain air, fire (from the igniting click) or existing portal blocks.
 */
public final class AbyssPortalShape {

    private static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private int numPortalBlocks;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private int width;

    public static Optional<AbyssPortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        return findPortalShape(level, pos, shape -> shape.isValid() && shape.numPortalBlocks == 0, axis);
    }

    public static Optional<AbyssPortalShape> findPortalShape(LevelAccessor level, BlockPos pos, Predicate<AbyssPortalShape> predicate, Direction.Axis axis) {
        Optional<AbyssPortalShape> onAxis = Optional.of(new AbyssPortalShape(level, pos, axis)).filter(predicate);
        if (onAxis.isPresent()) {
            return onAxis;
        }
        Direction.Axis other = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return Optional.of(new AbyssPortalShape(level, pos, other)).filter(predicate);
    }

    public AbyssPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        this.bottomLeft = calculateBottomLeft(pos);
        if (this.bottomLeft == null) {
            this.bottomLeft = pos;
            this.width = 1;
            this.height = 1;
        } else {
            this.width = calculateWidth();
            if (this.width > 0) {
                this.height = calculateHeight();
            }
        }
    }

    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos) {
        int minY = Math.max(level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (cursor.getY() > minY && isEmpty(level.getBlockState(cursor.below()))) {
            cursor.move(Direction.DOWN);
        }
        Direction leftDir = rightDir.getOpposite();
        int d = getDistanceUntilEdgeAboveFrame(cursor, leftDir) - 1;
        return d < 0 ? null : cursor.relative(leftDir, d).immutable();
    }

    private int calculateWidth() {
        int w = getDistanceUntilEdgeAboveFrame(bottomLeft, rightDir);
        return w >= MIN_WIDTH && w <= MAX_WIDTH ? w : 0;
    }

    /**
     * Walking from an interior position toward {@code dir}: each interior column
     * must be empty AND sit on a frame block (the portal floor). Stops when it
     * reaches the frame edge, returning the distance to it.
     */
    private int getDistanceUntilEdgeAboveFrame(BlockPos pos, Direction dir) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= MAX_WIDTH; i++) {
            m.set(pos).move(dir, i);
            BlockState state = level.getBlockState(m);
            if (!isEmpty(state)) {
                return isFrameBlock(state) ? i : 0;
            }
            BlockState below = level.getBlockState(m.move(Direction.DOWN));
            if (!isFrameBlock(below)) {
                return 0;
            }
        }
        return 0;
    }

    private int calculateHeight() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int h = getDistanceUntilTop(m);
        return h >= MIN_HEIGHT && h <= MAX_HEIGHT && hasTopFrame(m, h) ? h : 0;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos m, int h) {
        for (int i = 0; i < width; i++) {
            m.set(bottomLeft).move(Direction.UP, h).move(rightDir, i);
            if (!isFrameBlock(level.getBlockState(m))) {
                return false;
            }
        }
        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos m) {
        numPortalBlocks = 0;
        for (int i = 0; i < MAX_HEIGHT; i++) {
            // Left frame column.
            m.set(bottomLeft).move(Direction.UP, i).move(rightDir, -1);
            if (!isFrameBlock(level.getBlockState(m))) {
                return i;
            }
            // Right frame column.
            m.set(bottomLeft).move(Direction.UP, i).move(rightDir, width);
            if (!isFrameBlock(level.getBlockState(m))) {
                return i;
            }
            // Interior of this row must be empty.
            for (int j = 0; j < width; j++) {
                m.set(bottomLeft).move(Direction.UP, i).move(rightDir, j);
                BlockState state = level.getBlockState(m);
                if (!isEmpty(state)) {
                    return i;
                }
                if (state.is(ModBlocks.ABYSS_PORTAL.get())) {
                    numPortalBlocks++;
                }
            }
        }
        return MAX_HEIGHT;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir()
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(ModBlocks.ABYSS_PORTAL.get());
    }

    /** The Abyss portal frame is built from vanilla diamond and/or iron blocks. */
    public static boolean isFrame(LevelAccessor level, BlockPos pos) {
        return isFrameBlock(level.getBlockState(pos));
    }

    public static boolean isFrameBlock(BlockState state) {
        return state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.IRON_BLOCK);
    }

    public boolean isValid() {
        return bottomLeft != null && width >= MIN_WIDTH && width <= MAX_WIDTH
                && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    public void createPortalBlocks() {
        BlockState portalState = ModBlocks.ABYSS_PORTAL.get().defaultBlockState()
                .setValue(AbyssPortalBlock.AXIS, axis);
        BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(width - 1, height - 1, 0)).forEach(offset -> {
            BlockPos p = bottomLeft.relative(rightDir, offset.getX()).above(offset.getY());
            level.setBlock(p, portalState, 18);
        });
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Nullable
    public BlockPos getBottomLeft() {
        return bottomLeft;
    }

    public Direction.Axis getAxis() {
        return axis;
    }
}
