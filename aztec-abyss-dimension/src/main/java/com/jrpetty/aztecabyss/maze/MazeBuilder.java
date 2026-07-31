package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stamps the maze into a flat void dimension, a slice at a time.
 *
 * <p>The original mod generates the maze from a custom chunk generator. This one
 * stamps it instead, for the same reason the Abyss arena is stamped: the whole
 * mod already works that way, and a builder cannot get the generator's codec
 * registration wrong in a way that only shows up after a full CI cycle.
 *
 * <p>The catch is size. At six blocks a cell the map is 576 x 576 with walls
 * eighteen high - close to two million blocks, far too many to write in one
 * tick. So the build runs off a cursor: a few dozen cells per tick, finishing in
 * a handful of seconds, once, on first entry.
 *
 * <p>Toggle edges are deliberately built <em>closed</em>. The nightly reshape
 * then only has to move the two hundred toggles rather than rebuild the map -
 * about fourteen thousand blocks instead of two million.
 */
public final class MazeBuilder {

    /** Cells stamped per tick while a build is running. */
    private static final int CELLS_PER_TICK = 64;

    private static final BlockState FLOOR = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_WORN = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_MOSS = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState GLADE_GROUND = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    /** Cursor into the 96x96 cell grid; -1 means no build in progress. */
    private static int cursor = -1;

    private MazeBuilder() {
    }

    /** True once the maze has been stamped in this world. */
    public static boolean isBuilt(ServerLevel level) {
        return level.getBlockState(new BlockPos(MazeData.SPAWN_X, MazeData.FLOOR_Y, MazeData.SPAWN_Z))
                .is(Blocks.GRASS_BLOCK);
    }

    public static boolean isBuilding() {
        return cursor >= 0;
    }

    /** Kicks off a build if this world has never had one. */
    public static void beginIfNeeded(ServerLevel level) {
        MazeData.load();
        if (cursor < 0 && !isBuilt(level)) {
            cursor = 0;
        }
    }

    /** How far along a running build is, 0-100. */
    public static int progressPercent() {
        if (cursor < 0) {
            return 100;
        }
        return (int) (cursor * 100L / (MazeData.GRID * MazeData.GRID));
    }

    /**
     * Advances a running build by one slice. Safe to call every tick; does
     * nothing once the map is down.
     */
    public static void tick(ServerLevel level) {
        if (cursor < 0) {
            return;
        }
        int end = Math.min(cursor + CELLS_PER_TICK, MazeData.GRID * MazeData.GRID);
        RandomSource rng = RandomSource.create();
        for (int i = cursor; i < end; i++) {
            stampCell(level, i % MazeData.GRID, i / MazeData.GRID, rng);
        }
        cursor = end;
        if (cursor >= MazeData.GRID * MazeData.GRID) {
            cursor = -1;
            finish(level);
        }
    }

    /**
     * One cell: floor, then walls everywhere the corridor does not run.
     *
     * <p>Each cell only ever carves its own half of a shared edge - its outer two
     * blocks on that side - so neighbouring cells meet in the middle and no cell
     * needs to know whether its neighbour has been stamped yet. That is what
     * makes the build safe to interrupt and resume across ticks.
     */
    private static void stampCell(ServerLevel level, int cx, int cz, RandomSource rng) {
        int ox = cx * MazeData.CELL;
        int oz = cz * MazeData.CELL;
        boolean glade = MazeData.inGlade(cx, cz);

        boolean openW = MazeData.isOpen(cx, cz, cx - 1, cz, null) && cx > 0;
        boolean openE = MazeData.isOpen(cx, cz, cx + 1, cz, null) && cx < MazeData.GRID - 1;
        boolean openN = MazeData.isOpen(cx, cz, cx, cz - 1, null) && cz > 0;
        boolean openS = MazeData.isOpen(cx, cz, cx, cz + 1, null) && cz < MazeData.GRID - 1;

        for (int lx = 0; lx < MazeData.CELL; lx++) {
            for (int lz = 0; lz < MazeData.CELL; lz++) {
                int x = ox + lx;
                int z = oz + lz;
                level.setBlock(new BlockPos(x, MazeData.FLOOR_Y - 7, z), BEDROCK, 2);
                level.setBlock(new BlockPos(x, MazeData.FLOOR_Y, z),
                        glade ? GLADE_GROUND : FLOOR, 2);

                if (glade || isCorridor(lx, lz, openW, openE, openN, openS)) {
                    continue;
                }
                for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                    level.setBlock(new BlockPos(x, y, z), wallStone(rng), 2);
                }
            }
        }
    }

    /** Corridors run two wide down the middle of a cell, with arms into open edges. */
    private static boolean isCorridor(int lx, int lz,
                                      boolean openW, boolean openE, boolean openN, boolean openS) {
        boolean midX = lx >= MazeData.CORRIDOR_MIN && lx <= MazeData.CORRIDOR_MAX;
        boolean midZ = lz >= MazeData.CORRIDOR_MIN && lz <= MazeData.CORRIDOR_MAX;
        if (midX && midZ) {
            return true;
        }
        if (midZ && lx < MazeData.CORRIDOR_MIN) {
            return openW;
        }
        if (midZ && lx > MazeData.CORRIDOR_MAX) {
            return openE;
        }
        if (midX && lz < MazeData.CORRIDOR_MIN) {
            return openN;
        }
        if (midX && lz > MazeData.CORRIDOR_MAX) {
            return openS;
        }
        return false;
    }

    private static BlockState wallStone(RandomSource rng) {
        int r = rng.nextInt(12);
        return r < 7 ? WALL : r < 10 ? WALL_WORN : WALL_MOSS;
    }

    /** Seals the outer rim so the only way out is an exit the day has opened. */
    private static void finish(ServerLevel level) {
        int max = MazeData.SPAN - 1;
        for (int i = 0; i < MazeData.SPAN; i++) {
            for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                level.setBlock(new BlockPos(i, y, 0), BEDROCK, 2);
                level.setBlock(new BlockPos(i, y, max), BEDROCK, 2);
                level.setBlock(new BlockPos(0, y, i), BEDROCK, 2);
                level.setBlock(new BlockPos(max, y, i), BEDROCK, 2);
            }
        }
    }

    // ------------------------------------------------------------------
    // The nightly reshape
    // ------------------------------------------------------------------

    /**
     * Opens or closes a single toggle edge - the whole of what a reshape does.
     *
     * <p>A toggle sits on the boundary between two cells, so moving it means
     * writing both halves: the outer two blocks of each cell on that shared
     * side, two wide and eighteen high.
     */
    public static void setToggle(ServerLevel level, MazeData.TogglePoint tp, boolean open, RandomSource rng) {
        int[] cells = parseEdge(tp.edge());
        if (cells == null) {
            return;
        }
        writeHalf(level, cells[0], cells[1], cells[2], cells[3], open, rng);
        writeHalf(level, cells[2], cells[3], cells[0], cells[1], open, rng);
    }

    /** Carves (or refills) the half of a shared edge that belongs to one cell. */
    private static void writeHalf(ServerLevel level, int cx, int cz, int nx, int nz,
                                  boolean open, RandomSource rng) {
        int dx = Integer.signum(nx - cx);
        int dz = Integer.signum(nz - cz);
        for (int step = 0; step < MazeData.CORRIDOR_MIN; step++) {
            for (int across = MazeData.CORRIDOR_MIN; across <= MazeData.CORRIDOR_MAX; across++) {
                int lx = dx == 0 ? across : (dx > 0 ? MazeData.CELL - 1 - step : step);
                int lz = dz == 0 ? across : (dz > 0 ? MazeData.CELL - 1 - step : step);
                int x = cx * MazeData.CELL + lx;
                int z = cz * MazeData.CELL + lz;
                for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                    level.setBlock(new BlockPos(x, y, z),
                            open ? Blocks.AIR.defaultBlockState() : wallStone(rng), 2);
                }
            }
        }
    }

    /** "3,92>4,92" into {3, 92, 4, 92}, or null if it will not parse. */
    static int[] parseEdge(String edge) {
        int arrow = edge.indexOf('>');
        if (arrow < 0) {
            return null;
        }
        String[] a = edge.substring(0, arrow).split(",");
        String[] b = edge.substring(arrow + 1).split(",");
        if (a.length != 2 || b.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()),
                    Integer.parseInt(b[0].trim()), Integer.parseInt(b[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
