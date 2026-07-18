package com.jrpetty.aztecabyss.block;

import com.jrpetty.aztecabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Frame-detection and fill logic for the Abyss portal, adapted from vanilla's
 * nether portal shape algorithm but keyed to {@link ModBlocks#ABYSSAL_OBSIDIAN}
 * instead of obsidian, and filling with {@link ModBlocks#ABYSS_PORTAL}.
 *
 * The frame does NOT need to be a specific shape beyond: a rectangular ring of
 * abyssal obsidian, minimum interior 1x1, maximum interior 21x21, all four
 * sides fully framed, corners not required to be frame blocks (matches vanilla
 * nether portal behaviour).
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
        Direction.Axis otherAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return Optional.of(new AbyssPortalShape(level, pos, otherAxis)).filter(predicate);
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
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int y = cursor.getY();
        for (; y > level.getMinY() && isEmptyOrPortal(level, cursor.below()); y--) {
            cursor.move(Direction.DOWN);
        }
        Direction leftDir = rightDir.getOpposite();
        int distanceToEdge = getDistanceUntilEdgeAboveFrame(cursor, leftDir) - 1;
        if (distanceToEdge < 0) {
            return null;
        }
        return cursor.relative(leftDir, distanceToEdge).immutable();
    }

    private int getDistanceUntilEdgeAboveFrame(BlockPos pos, Direction dir) {
        for (int i = 0; i <= MAX_WIDTH; i++) {
            BlockPos check = pos.relative(dir, i);
            if (!isEmptyOrPortal(level, check) || !isEmptyOrPortal(level, check.below())) {
                if (isFrame(level, check)) {
                    return i;
                }
                break;
            }
        }
        return -1;
    }

    private int calculateWidth() {
        int max = getDistanceUntilEdgeAboveFrame(bottomLeft, rightDir);
        return max >= MIN_WIDTH && max <= MAX_WIDTH ? max : 0;
    }

    private int calculateHeight() {
        int minY = level.getMinY();
        numPortalBlocks = 0;
        int h;
        for (h = 0; h < MAX_HEIGHT; h++) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int w = 0; w < width; w++) {
                cursor.set(bottomLeft).move(Direction.UP, h).move(rightDir, w);
                BlockState state = level.getBlockState(cursor);
                if (!isEmptyOrPortal(level, cursor)) {
                    return checkTop(h);
                }
                if (state.is(ModBlocks.ABYSS_PORTAL.get())) {
                    numPortalBlocks++;
                }
                if (w == 0 && !isFrame(level, cursor.relative(rightDir.getOpposite()))) {
                    return checkTop(h);
                }
                if (w == width - 1 && !isFrame(level, cursor.relative(rightDir))) {
                    return checkTop(h);
                }
            }
        }
        return checkTop(h);
    }

    private int checkTop(int h) {
        if (h < MIN_HEIGHT) {
            return 0;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int w = 0; w < width; w++) {
            cursor.set(bottomLeft).move(Direction.UP, h).move(rightDir, w);
            if (!isFrame(level, cursor)) {
                return 0;
            }
        }
        return h;
    }

    private static boolean isEmptyOrPortal(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(ModBlocks.ABYSS_PORTAL.get());
    }

    private static boolean isFrame(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.ABYSSAL_OBSIDIAN.get());
    }

    public boolean isValid() {
        return bottomLeft != null && width >= MIN_WIDTH && width <= MAX_WIDTH
                && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    public void createPortalBlocks() {
        BlockState portalState = ModBlocks.ABYSS_PORTAL.get().defaultBlockState()
                .setValue(AbyssPortalBlock.AXIS, axis);
        BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(width - 1, height - 1, 0)).forEach(offset -> {
            BlockPos pos = bottomLeft.relative(rightDir, offset.getX()).above(offset.getY());
            level.setBlock(pos, portalState, 18);
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
