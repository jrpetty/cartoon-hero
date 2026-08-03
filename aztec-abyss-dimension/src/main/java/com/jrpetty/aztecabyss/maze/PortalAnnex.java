package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.server.level.ServerLevel;

/**
 * The way out, and the last thing between you and it.
 *
 * <h2>Why the maze stops being square here</h2>
 *
 * <p>The maze is a perfect ninety-six by ninety-six square, which is right - it
 * is meant to read as something built rather than grown, and a square is the
 * most built shape there is. But the exit was a hole punched in that square's rim
 * and a lantern hung over it, so the most important place in the map was
 * architecturally identical to a wall with a gap in it. You could walk into the
 * end of your fourth day's search and not be sure you had found anything.
 *
 * <p>So the exit is the one place the square breaks. It protrudes: a long walled
 * lane running out of the maze into ground that is not maze at all, and a chamber
 * at the end of it. From inside a corridor the doorway now leads somewhere that
 * is visibly not more corridor, which is the entire job the exit was failing to
 * do.
 *
 * <h2>Why there is an army in it</h2>
 *
 * <p>Finding the way out was the whole game and also the end of it - you stepped
 * through and won. That makes the maze a search puzzle with no climax: the
 * hardest thing you ever do is the walking, and the moment of arrival asks
 * nothing of you at all.
 *
 * <p>Forty blocks of lane, ten wide, with sixty of them standing in it. You have
 * found the way out and you cannot have it. Everything the Glade spent the week
 * building - the forged gear, the rations, whoever is still alive - is for this,
 * and it happens in a corridor narrow enough that four people can hold a line and
 * one person almost certainly cannot.
 */
public final class PortalAnnex {

    /** How far the lane runs out of the maze. */
    public static final int LANE_LENGTH = 40;
    /** Half the lane's interior width: ten blocks across. */
    private static final int LANE_HALF = 5;
    /** The chamber at the end. */
    private static final int ROOM_DEPTH = 16;
    private static final int ROOM_HALF = 8;

    /** How many rows one exit's annex is stamped in. */
    private static final int ROWS_PER_EXIT = LANE_LENGTH + ROOM_DEPTH + 1;

    public static final String[] EXIT_IDS =
            {"exit_0", "exit_1", "exit_2", "exit_3", "exit_4", "exit_5", "exit_6"};

    /** How far outside the maze square an annex reaches, for sweeps. */
    public static final int REACH = LANE_LENGTH + ROOM_DEPTH + 8;

    /** Total build slices across every exit. */
    public static final int SLICES = ROWS_PER_EXIT * EXIT_IDS.length;

    private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState TRIM = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

    private PortalAnnex() {
    }

    // ------------------------------------------------------------------
    // Where things are
    // ------------------------------------------------------------------

    private static int outX(String facing) {
        return "west".equals(facing) ? -1 : "east".equals(facing) ? 1 : 0;
    }

    private static int outZ(String facing) {
        return "north".equals(facing) ? -1 : "south".equals(facing) ? 1 : 0;
    }

    private static int perX(String facing) {
        return outZ(facing) != 0 ? 1 : 0;
    }

    private static int perZ(String facing) {
        return outX(facing) != 0 ? 1 : 0;
    }

    /** A point in an exit's annex: {@code d} blocks out, {@code w} blocks across. */
    private static BlockPos at(MazeData.Exit ex, int d, int w, int y) {
        int[] p = MazeData.exitPortal(ex);
        String f = ex.facing();
        return new BlockPos(
                p[0] + outX(f) * d + perX(f) * w,
                y,
                p[2] + outZ(f) * d + perZ(f) * w);
    }

    /** Where the way out actually stands: the far end of the chamber. */
    public static BlockPos portalPos(MazeData.Exit ex) {
        return at(ex, LANE_LENGTH + ROOM_DEPTH - 2, 0, MazeData.FLOOR_Y + 1);
    }

    /**
     * True if this position is inside the lane - the trigger for the horde.
     *
     * <p>Measured in the annex's own coordinates rather than as a box, because a
     * box in world space would be four different boxes depending on which way the
     * exit faces and would be wrong for at least one of them.
     */
    public static boolean inLane(MazeData.Exit ex, BlockPos where) {
        int[] p = MazeData.exitPortal(ex);
        String f = ex.facing();
        int dx = where.getX() - p[0];
        int dz = where.getZ() - p[2];
        int d = outX(f) != 0 ? dx * outX(f) : dz * outZ(f);
        int w = perX(f) != 0 ? dx : dz;
        return d >= 1 && d <= LANE_LENGTH + ROOM_DEPTH
                && Math.abs(w) <= ROOM_HALF
                && Math.abs(where.getY() - MazeData.FLOOR_Y) <= 20;
    }

    /** A spot in the lane to stand something in, spread along its length. */
    public static BlockPos laneSpot(MazeData.Exit ex, int index, int total) {
        int d = 4 + (index * (LANE_LENGTH - 6)) / Math.max(1, total);
        int w = -LANE_HALF + 1 + (index * 7919) % (LANE_HALF * 2 - 2);
        return at(ex, d, w, MazeData.FLOOR_Y + 1);
    }

    // ------------------------------------------------------------------
    // Building it
    // ------------------------------------------------------------------

    /**
     * Stamps one row of one exit's annex.
     *
     * <p>Sliced by row so it rides the maze builder's existing staging. Seven
     * annexes at eight hundred columns each is not something to do on one tick,
     * and it is not something to do lazily either - an annex that appears when
     * you arrive is an annex you can watch appear.
     */
    public static void stampSlice(ServerLevel level, int slice) {
        int which = slice / ROWS_PER_EXIT;
        int d = slice % ROWS_PER_EXIT + 1;
        if (which >= EXIT_IDS.length) {
            return;
        }
        MazeData.Exit ex = MazeData.exit(EXIT_IDS[which]);
        if (ex == null) {
            return;
        }
        boolean inRoom = d > LANE_LENGTH;
        boolean firstRoomRow = d == LANE_LENGTH + 1;
        boolean lastRow = d == LANE_LENGTH + ROOM_DEPTH;
        int half = inRoom ? ROOM_HALF : LANE_HALF;

        for (int w = -half - 1; w <= half; w++) {
            boolean sideWall = w == -half - 1 || w == half;
            // The chamber is wider than the lane, so its first row has to close
            // off everything either side of the lane's mouth - otherwise the room
            // opens straight out into nothing at the corners.
            boolean backWall = firstRoomRow && Math.abs(w) > LANE_HALF;
            boolean solid = sideWall || backWall || lastRow;

            level.setBlock(at(ex, d, w, MazeData.FLOOR_Y - 7), Blocks.BEDROCK.defaultBlockState(), 2);
            level.setBlock(at(ex, d, w, MazeData.FLOOR_Y), FLOOR, 2);
            for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                level.setBlock(at(ex, d, w, y),
                        solid ? wallAt(y) : Blocks.AIR.defaultBlockState(), 2);
            }
            // Same lid as the corridors, so the annex is as unclimbable as the
            // maze it hangs off.
            level.setBlock(at(ex, d, w, MazeData.WALL_TOP_Y + 1),
                    Blocks.BARRIER.defaultBlockState(), 2);
        }

        dress(level, ex, d, inRoom, lastRow);
    }

    private static BlockState wallAt(int y) {
        return y >= MazeData.WALL_TOP_Y - 1 ? TRIM : WALL;
    }

    /**
     * The parts that make it read as a place rather than a tunnel.
     *
     * <p>Light down one side at long intervals, because a forty-block lane lit
     * evenly is a corridor and a forty-block lane with pools of dark between the
     * lamps is somewhere sixty things could be standing.
     */
    private static void dress(ServerLevel level, MazeData.Exit ex, int d, boolean inRoom, boolean lastRow) {
        if (!inRoom && d % 8 == 0) {
            level.setBlock(at(ex, d, -LANE_HALF, MazeData.WALL_BASE_Y + 4),
                    Blocks.LANTERN.defaultBlockState(), 2);
            level.setBlock(at(ex, d, LANE_HALF - 1, MazeData.WALL_BASE_Y + 4),
                    Blocks.LANTERN.defaultBlockState(), 2);
        }
        if (!lastRow) {
            return;
        }
        // The way out, built like it matters: a lit dais, a frame, and the one
        // block of colour anywhere in this dimension.
        BlockPos portal = portalPos(ex);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 4) {
                    continue;
                }
                level.setBlock(portal.offset(dx, -1, dz), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        level.setBlock(portal.below(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        for (int y = 0; y <= 4; y++) {
            level.setBlock(portal.above(y), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(portal.above(5), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(portal.above(4), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
    }
}
