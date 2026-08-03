package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Where the Grievers go.
 *
 * <p>They had one behaviour: appear near a runner, chase them, and at dawn be
 * teleported to a map corner and deleted. Both ends of that were nothing. They
 * came from nowhere, which meant the maze had no geography of danger - every
 * corridor was equally likely to produce one, so no corridor was frightening for
 * a reason. And they left by being removed, which is the least interesting way
 * for a monster to stop existing.
 *
 * <p>A Griever hole fixes both ends with one object. Sixteen of them, sunk
 * through the floor at fixed points across the map, each a shaft down into a
 * chamber. They come <em>up</em> out of the nearest one at dusk and they go back
 * down the nearest one at dawn, in front of you, with the sound of something
 * heavy going into a hole.
 *
 * <h2>What that buys</h2>
 *
 * <p>The map acquires danger you can learn. Knowing where the holes are is worth
 * something a chart cannot tell you - a route that threads between four of them
 * is a bad route however short it is, and a Runner who has charted the holes has
 * knowledge the Glade will actually plan around.
 *
 * <p>It also gives dawn a picture. "The scraping fades" was a line of text; a
 * dozen things turning round mid-chase and walking away down a hole is an event.
 */
public final class GrieverHoles {

    /** Holes across the maze, laid out on a four by four grid. */
    private static final int PER_AXIS = 4;
    public static final int COUNT = PER_AXIS * PER_AXIS;

    /** Rows stamped per hole while the map is being built. */
    private static final int ROWS_PER_HOLE = 12;
    public static final int SLICES = COUNT * ROWS_PER_HOLE;

    /** How far below the corridor floor the chamber sits. */
    private static final int DEPTH = 9;

    private GrieverHoles() {
    }

    /**
     * Where hole {@code i} is.
     *
     * <p>Spread evenly and then nudged by an odd multiplier so the grid is not
     * legible as a grid - sixteen holes at exactly regular intervals would be a
     * pattern you learn once and never think about again, which is the opposite
     * of the point.
     */
    public static BlockPos hole(int i) {
        int gx = i % PER_AXIS;
        int gz = i / PER_AXIS;
        int step = MazeData.GRID / PER_AXIS;
        int cellX = gx * step + step / 2 + (i * 7) % 5 - 2;
        int cellZ = gz * step + step / 2 + (i * 11) % 5 - 2;
        cellX = Math.max(2, Math.min(MazeData.GRID - 3, cellX));
        cellZ = Math.max(2, Math.min(MazeData.GRID - 3, cellZ));
        // Never in the clearing. A hole in the Glade would make the one safe
        // place on the map the most dangerous one on it.
        if (MazeData.inGlade(cellX, cellZ)) {
            cellX = cellX < MazeData.GRID / 2 ? MazeData.GLADE_MIN_CELL - 4 : MazeData.GLADE_MAX_CELL + 4;
        }
        return new BlockPos(
                MazeData.corridorMin(cellX) + 1,
                MazeData.FLOOR_Y,
                MazeData.corridorMin(cellZ) + 1);
    }

    /** The nearest hole to a position, for coming up out of or going back down. */
    public static BlockPos nearest(BlockPos to) {
        BlockPos best = hole(0);
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < COUNT; i++) {
            BlockPos h = hole(i);
            double d = h.distSqr(to);
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /** The nearest hole that is at least {@code min} blocks away. */
    public static BlockPos nearestBeyond(BlockPos to, int min) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        double floor = (double) min * min;
        for (int i = 0; i < COUNT; i++) {
            BlockPos h = hole(i);
            double d = h.distSqr(to);
            if (d >= floor && d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best == null ? nearest(to) : best;
    }

    /**
     * Stamps one row of one hole.
     *
     * <p>The floor is otherwise unbreakable and unbroken, so a hole is the only
     * hole in it anywhere - which is what makes it read as a thing rather than as
     * terrain.
     */
    public static void stampSlice(ServerLevel level, int slice) {
        int which = slice / ROWS_PER_HOLE;
        int row = slice % ROWS_PER_HOLE;
        if (which >= COUNT) {
            return;
        }
        BlockPos h = hole(which);

        if (row == 0) {
            // The rim: a ring of cracked deepslate so the mouth is visible from
            // the far end of a corridor rather than being a dark square you walk
            // into at a sprint.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        continue;
                    }
                    level.setBlock(h.offset(dx, 0, dz),
                            Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), 2);
                }
            }
            // And the mouth itself. The floor is stamped solid everywhere by the
            // cell pass, so without this the hole is a shaft with a lid on it.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(h.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            return;
        }
        int y = MazeData.FLOOR_Y - row;
        if (y <= MazeData.FLOOR_Y - DEPTH) {
            // The chamber floor, and a light in it that does not reach the top.
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    level.setBlock(new BlockPos(h.getX() + dx, y - 1, h.getZ() + dz),
                            Blocks.BEDROCK.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(h.getX() + dx, y, h.getZ() + dz),
                            Math.abs(dx) == 3 || Math.abs(dz) == 3
                                    ? Blocks.DEEPSLATE.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState(), 2);
                }
            }
            level.setBlock(new BlockPos(h.getX(), y, h.getZ()),
                    Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            return;
        }
        // The shaft: three by three, walled in deepslate.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean inside = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                level.setBlock(new BlockPos(h.getX() + dx, y, h.getZ() + dz),
                        inside ? Blocks.AIR.defaultBlockState()
                                : Blocks.DEEPSLATE.defaultBlockState(), 2);
            }
        }
        // A ladder the whole way up. Without one the hole is a pit with an
        // unbreakable floor and unbreakable walls, which is not a Griever hole,
        // it is a place a player is deleted for being curious.
        level.setBlock(new BlockPos(h.getX() + 1, y, h.getZ()),
                Blocks.LADDER.defaultBlockState().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                        net.minecraft.core.Direction.WEST), 2);
    }

    /**
     * Solid ground beside a hole to put something on.
     *
     * <p>Spawning one <em>in</em> the mouth would drop it straight back down the
     * shaft it was supposed to be climbing out of. Searched rather than hard
     * coded, because which sides of a hole are corridor and which are wall
     * depends on the cell it landed in.
     */
    public static BlockPos mouthSpawn(ServerLevel level, BlockPos h) {
        for (int r = 2; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                        continue;
                    }
                    BlockPos foot = new BlockPos(h.getX() + dx, MazeData.FLOOR_Y + 1, h.getZ() + dz);
                    if (level.getBlockState(foot).isAir()
                            && level.getBlockState(foot.above()).isAir()
                            && !level.getBlockState(foot.below()).isAir()) {
                        return foot;
                    }
                }
            }
        }
        return null;
    }
}
