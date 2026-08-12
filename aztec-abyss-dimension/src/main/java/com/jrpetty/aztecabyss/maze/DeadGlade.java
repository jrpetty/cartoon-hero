package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Dead Glade: somebody else's clearing, and what became of it.
 *
 * <p>The maze had exactly two destinations - the way out, and back home - and
 * everything between them was corridor you crossed as fast as you could. That
 * makes exploration a pure cost: the only reason to turn down an unknown
 * corridor was that it might be the way out, and if it wasn't, the trip was
 * wasted. A map with one prize in it is a puzzle, not a place.
 *
 * <p>So there is a second clearing out there. A group came up before you, built
 * the same things you built, and did not get out. It is smaller than yours,
 * ruined, and it is the only place in the maze that is worth reaching for a
 * reason other than escape.
 *
 * <h2>Where it is, and why not the middle</h2>
 *
 * <p>The obvious answer is the centre of the map. The centre of the map is your
 * Glade - cells 40 to 55 of 96, dead middle - so the Dead Glade sits deep in the
 * south-west instead, about twenty cells past your own wall and at least twenty
 * from any exit. Far enough that reaching it is a decision about how to spend a
 * day, and not on the way to anything.
 *
 * <h2>What is in it</h2>
 *
 * <p>Their charts, which is the real reward. Reading the lectern in their map
 * room marks everything they had surveyed onto your Glade's chart - a large
 * region around their clearing, free, that your Runners would otherwise spend
 * days walking. Once per game, and it is worth the trip.
 *
 * <p>And their supplies, which they did not get to use.
 *
 * <h2>It stays put; the way in does not</h2>
 *
 * <p>The camp is fixed - the same cells, the same buildings, for the life of the
 * world - and the maze reshapes around it every night like it reshapes around
 * everything else. What changes is where its wall has fallen in: three breaches,
 * chosen from its own name so a preset always opens the same ones, and no two of
 * the seven presets share a set. Learning "on day four you go in from the east"
 * is a real thing a veteran can know.
 *
 * <p>The breaches are picked from the ones that open onto corridor a runner can
 * actually reach, not by hash alone. That distinction is not theoretical: on one
 * of the seven presets, hash-alone put every single breach on unreachable
 * corridor, and the camp would simply have been sealed for a day with nothing to
 * say why.
 */
public final class DeadGlade {

    /** North-west corner, in cells. Deep south-west, clear of every exit. */
    public static final int CELL_X = 16;
    public static final int CELL_Z = 70;
    /**
     * Ten cells against your sixteen.
     *
     * <p>Smaller on purpose. It has to read as the same kind of place without
     * competing with home, and a ruin the size of the Glade would be a second
     * settlement rather than a warning.
     */
    public static final int SPAN_CELLS = 10;

    private static final int Y = MazeData.FLOOR_Y;

    /** How far their survey reached, in cells, from the middle of their camp. */
    private static final int CHART_RADIUS = 14;

    private DeadGlade() {
    }

    public static int minBlock() {
        return CELL_X * MazeData.CELL;
    }

    public static int maxBlock() {
        return (CELL_X + SPAN_CELLS) * MazeData.CELL - 1;
    }

    public static int minBlockZ() {
        return CELL_Z * MazeData.CELL;
    }

    public static int maxBlockZ() {
        return (CELL_Z + SPAN_CELLS) * MazeData.CELL - 1;
    }

    public static int centreX() {
        return (minBlock() + maxBlock()) / 2;
    }

    public static int centreZ() {
        return (minBlockZ() + maxBlockZ()) / 2;
    }

    /** Where their charts are kept. The one block here worth right-clicking. */
    public static BlockPos lectern() {
        return new BlockPos(centreX() + 8, Y + 1, centreZ() - 6);
    }

    /**
     * Does the camp stand on this cell?
     *
     * <p>Asked by the nightly reshape. Two of the two hundred toggle points sit
     * inside this footprint, and without this they would grow a wall back
     * through the middle of the camp every night - in a clearing whose walls
     * are supposed to be gone for good.
     */
    public static boolean coversCell(int cellX, int cellZ) {
        return cellX >= CELL_X && cellX < CELL_X + SPAN_CELLS
                && cellZ >= CELL_Z && cellZ < CELL_Z + SPAN_CELLS;
    }

    public static boolean inside(BlockPos at) {
        return at.getX() >= minBlock() && at.getX() <= maxBlock()
                && at.getZ() >= minBlockZ() && at.getZ() <= maxBlockZ();
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    public static void build(ServerLevel level) {
        RandomSource rng = RandomSource.create(0xDEAD61A);
        clear(level);
        ground(level, rng);
        theirBox(level);
        ruinedHuts(level, rng);
        deadField(level, rng);
        graves(level, rng);
        mapRoom(level);
        rubble(level, rng);
        // Walled last, so nothing scattered writes over the perimeter. The
        // breaches themselves are cut by the reshape, which runs straight after
        // the build and every night after that.
        wallRing(level, rng);
    }

    /** Empties the cells the camp stands in, walls and all. */
    private static void clear(ServerLevel level) {
        for (int x = minBlock(); x <= maxBlock(); x++) {
            for (int z = minBlockZ(); z <= maxBlockZ(); z++) {
                for (int dy = 1; dy <= MazeData.WALL_TOP_Y - Y; dy++) {
                    level.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Ground that lost.
     *
     * <p>Your Glade is grass with worn paths through it. This is the same
     * clearing after nobody tended it: podzol and coarse dirt where the paths
     * were, gravel where things fell, and the grass only holding on at the
     * edges. The contrast is the whole point - it should be recognisably the
     * same kind of place and obviously finished.
     */
    private static void ground(ServerLevel level, RandomSource rng) {
        int cx = centreX();
        int cz = centreZ();
        for (int x = minBlock(); x <= maxBlock(); x++) {
            for (int z = minBlockZ(); z <= maxBlockZ(); z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                int h = Math.floorMod(x * 40503 ^ z * 26861, 100);
                BlockState top;
                if (h < 8) {
                    top = Blocks.GRAVEL.defaultBlockState();
                } else if (d < 22 && h < 55) {
                    top = Blocks.PODZOL.defaultBlockState();
                } else if (h < 74) {
                    top = Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    top = Blocks.GRASS_BLOCK.defaultBlockState();
                }
                level.setBlock(new BlockPos(x, Y, z), top, 2);
                // Their ground is soil too, and the same three deep.
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y - 2, z), Blocks.DIRT.defaultBlockState(), 2);
                if (h >= 92 && top.is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(new BlockPos(x, Y + 1, z),
                            Blocks.DEAD_BUSH.defaultBlockState(), 2);
                } else if (h == 3) {
                    level.setBlock(new BlockPos(x, Y + 1, z),
                            Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Their Box, fallen in.
     *
     * <p>The same deepslate cage as yours, which is the detail that should land
     * hardest: it is not a different structure, it is your structure with the
     * bars torn open and the lift never coming again.
     */
    private static void theirBox(ServerLevel level) {
        int cx = centreX();
        int cz = centreZ();
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean edge = x == cx - 3 || x == cx + 3 || z == cz - 3 || z == cz + 3;
                level.setBlock(new BlockPos(x, Y, z), edge
                        ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                if (!edge) {
                    continue;
                }
                boolean corner = (x == cx - 3 || x == cx + 3) && (z == cz - 3 || z == cz + 3);
                // Broken off at different heights, so it reads as collapsed
                // rather than as a shorter cage.
                int standing = corner ? 3 : Math.floorMod(x * 31 + z * 17, 4);
                for (int dy = 1; dy <= standing; dy++) {
                    level.setBlock(new BlockPos(x, Y + dy, z), corner
                            ? Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState()
                            : Blocks.IRON_BARS.defaultBlockState(), 2);
                }
            }
        }
        // What is left of them, and what they left behind.
        level.setBlock(new BlockPos(cx, Y + 1, cz), Blocks.CHEST.defaultBlockState(), 2);
        fill(level, new BlockPos(cx, Y + 1, cz),
                new ItemStack(Items.IRON_INGOT, 6),
                new ItemStack(Items.STRING, 8),
                new ItemStack(Items.TORCH, 16),
                MazeSerum.create());
        level.setBlock(new BlockPos(cx - 1, Y + 1, cz + 1), Blocks.COBWEB.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx + 1, Y + 2, cz - 1), Blocks.COBWEB.defaultBlockState(), 2);
        sign(level, new BlockPos(cx, Y + 2, cz - 3), Direction.SOUTH,
                "§8— THE BOX —", "§7It stopped", "§7coming.", "");
    }

    /** Three shells of huts, no roofs left worth the name. */
    private static void ruinedHuts(ServerLevel level, RandomSource rng) {
        int ox = minBlock() + 6;
        int oz = minBlockZ() + 6;
        ruinedHut(level, rng, ox, oz, 8, 6);
        ruinedHut(level, rng, ox + 12, oz + 2, 6, 6);
        ruinedHut(level, rng, ox + 2, oz + 11, 7, 5);
    }

    private static void ruinedHut(ServerLevel level, RandomSource rng, int ox, int oz, int w, int d) {
        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean edge = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
                level.setBlock(new BlockPos(x, Y, z), rng.nextInt(4) == 0
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.OAK_PLANKS.defaultBlockState(), 2);
                if (!edge) {
                    continue;
                }
                // Walls that gave up at different heights, with gaps knocked
                // through. A ruin with an even skyline is a short building.
                int standing = corner ? 3 : rng.nextInt(4);
                for (int dy = 1; dy <= standing; dy++) {
                    if (!corner && rng.nextInt(6) == 0) {
                        continue; // a hole, rather than a shorter wall
                    }
                    level.setBlock(new BlockPos(x, Y + dy, z), corner
                            ? Blocks.OAK_LOG.defaultBlockState()
                            : Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
            }
        }
        // A few rafters still up, and the rest on the floor.
        for (int x = ox; x < ox + w; x++) {
            if (rng.nextInt(3) == 0) {
                level.setBlock(new BlockPos(x, Y + 4, oz + d / 2), Blocks.OAK_SLAB.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz + 1), Blocks.COBWEB.defaultBlockState(), 2);
        if (rng.nextInt(2) == 0) {
            level.setBlock(new BlockPos(ox + w - 2, Y + 1, oz + 1), Blocks.BARREL.defaultBlockState(), 2);
            fill(level, new BlockPos(ox + w - 2, Y + 1, oz + 1),
                    new ItemStack(Items.BREAD, 3),
                    new ItemStack(Items.WHEAT_SEEDS, 8));
        }
    }

    /** A field that went to seed, still fenced by somebody who cared. */
    private static void deadField(ServerLevel level, RandomSource rng) {
        int ox = maxBlock() - 18;
        int oz = maxBlockZ() - 16;
        for (int x = ox; x < ox + 14; x++) {
            for (int z = oz; z < oz + 10; z++) {
                level.setBlock(new BlockPos(x, Y, z), rng.nextInt(3) == 0
                        ? Blocks.FARMLAND.defaultBlockState()
                        : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                if (rng.nextInt(5) == 0) {
                    level.setBlock(new BlockPos(x, Y + 1, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);
                }
            }
        }
        // The fence, mostly still standing. Mostly is what makes it sad.
        for (int x = ox - 1; x <= ox + 14; x++) {
            if (rng.nextInt(5) > 0) {
                level.setBlock(new BlockPos(x, Y + 1, oz - 1), Blocks.OAK_FENCE.defaultBlockState(), 2);
            }
        }
        for (int z = oz - 1; z <= oz + 10; z++) {
            if (rng.nextInt(5) > 0) {
                level.setBlock(new BlockPos(ox - 1, Y + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
            }
        }
        sign(level, new BlockPos(ox + 3, Y + 1, oz - 1), Direction.NORTH,
                "§2THE FIELD", "§7We ate well", "§7for a while.", "");
    }

    /**
     * Their Deadheads, and it is bigger than yours.
     *
     * <p>Twenty-two graves for a camp of this size. Nobody has to say what
     * happened here.
     */
    private static void graves(ServerLevel level, RandomSource rng) {
        int ox = minBlock() + 4;
        int oz = maxBlockZ() - 20;
        for (int i = 0; i < 22; i++) {
            int x = ox + rng.nextInt(16);
            int z = oz + rng.nextInt(14);
            if (!level.getBlockState(new BlockPos(x, Y + 1, z)).isAir()) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(new BlockPos(x + dx, Y, z + dz),
                            Blocks.PODZOL.defaultBlockState(), 2);
                }
            }
            level.setBlock(new BlockPos(x, Y, z), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, Y + 1, z), rng.nextInt(4) == 0
                    ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()
                    : Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
        }
        sign(level, new BlockPos(ox + 8, Y + 1, oz - 1), Direction.SOUTH,
                "§8ALL OF THEM", "§7We stopped", "§7counting.", "");
    }

    /**
     * Their map room, and the charts inside it.
     *
     * <p>Deepslate, like yours, because it is the one thing any group in here
     * would build to last. The lectern is the reason the whole camp exists.
     */
    private static void mapRoom(ServerLevel level) {
        BlockPos lec = lectern();
        int ox = lec.getX() - 4;
        int oz = lec.getZ() - 3;
        for (int x = ox; x <= ox + 7; x++) {
            for (int z = oz; z <= oz + 6; z++) {
                boolean edge = x == ox || x == ox + 7 || z == oz || z == oz + 6;
                level.setBlock(new BlockPos(x, Y, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
                if (!edge) {
                    continue;
                }
                for (int dy = 1; dy <= 4; dy++) {
                    // Still standing, unlike everything else here. Cracked, not
                    // collapsed: they built this one properly.
                    level.setBlock(new BlockPos(x, Y + dy, z),
                            (x + z + dy) % 5 == 0
                                    ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                                    : Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(x, Y + 5, z),
                        Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState(), 2);
            }
        }
        // A doorway on the south face, and a roof over the middle.
        for (int dy = 1; dy <= 2; dy++) {
            level.setBlock(new BlockPos(ox + 3, Y + dy, oz + 6), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(ox + 4, Y + dy, oz + 6), Blocks.AIR.defaultBlockState(), 2);
        }
        for (int x = ox + 1; x <= ox + 6; x++) {
            for (int z = oz + 1; z <= oz + 5; z++) {
                level.setBlock(new BlockPos(x, Y + 5, z),
                        Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(ox + 1, Y + 4, oz + 1), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
        level.setBlock(new BlockPos(ox + 6, Y + 4, oz + 5), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);

        level.setBlock(lec, Blocks.LECTERN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
        sign(level, new BlockPos(lec.getX(), Y + 2, lec.getZ() - 1), Direction.SOUTH,
                "§6THE CHARTS", "§7Everything", "§7we mapped.", "§8Take them.");
    }

    /** Fallen stone scattered out from the walls the clearing ate. */
    private static void rubble(ServerLevel level, RandomSource rng) {
        for (int i = 0; i < 90; i++) {
            int x = minBlock() + rng.nextInt(SPAN_CELLS * MazeData.CELL);
            int z = minBlockZ() + rng.nextInt(SPAN_CELLS * MazeData.CELL);
            BlockPos at = new BlockPos(x, Y + 1, z);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            int roll = rng.nextInt(10);
            BlockState piece = roll < 4 ? Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState()
                    : roll < 7 ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
                    : roll < 9 ? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState()
                    : Blocks.STONE_BRICK_SLAB.defaultBlockState();
            level.setBlock(at, piece, 2);
        }
    }

    // ------------------------------------------------------------------
    // The wall, and the ways in
    // ------------------------------------------------------------------

    /** Breaches cut per night. Enough to vary the approach, few enough to hunt. */
    private static final int BREACHES = 3;
    /** Ten possible breaches a side, four sides. */
    private static final int PER_SIDE = SPAN_CELLS;
    public static final int CANDIDATES = PER_SIDE * 4;

    /**
     * The camp's perimeter, which it did not have and badly needed.
     *
     * <p>The first version simply cleared ten cells of maze, walls and all. That
     * made the camp a sixty-block hole open on all four faces - so the maze did
     * not route around it, it just stopped, and the presets had nothing to say
     * about how you got in. A landmark with no edge is not a landmark.
     *
     * <p>Now it is walled like your own Glade is walled, in the maze's own
     * stone, and the only ways in are the places the wall has fallen. Which
     * places those are changes every night with the reshape.
     */
    private static void wallRing(ServerLevel level, RandomSource rng) {
        for (int x = minBlock(); x <= maxBlock(); x++) {
            column(level, rng, x, minBlockZ());
            column(level, rng, x, maxBlockZ());
        }
        for (int z = minBlockZ(); z <= maxBlockZ(); z++) {
            column(level, rng, minBlock(), z);
            column(level, rng, maxBlock(), z);
        }
    }

    private static void column(ServerLevel level, RandomSource rng, int x, int z) {
        for (int y = Y + 1; y <= MazeData.WALL_TOP_Y; y++) {
            int r = rng.nextInt(10);
            // Older and sorrier than the live maze: mossy and cracked far more
            // often than the corridors you walked to get here.
            level.setBlock(new BlockPos(x, y, z), r < 4
                    ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : r < 7 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
    }

    /** Which side candidate {@code i} is on: 0 west, 1 east, 2 north, 3 south. */
    private static int side(int i) {
        return i / PER_SIDE;
    }

    private static int along(int i) {
        return i % PER_SIDE;
    }

    /** The maze cell a breach opens onto, outside the wall. */
    public static int[] candidateOutside(int i) {
        int k = along(i);
        return switch (side(i)) {
            case 0 -> new int[]{CELL_X - 1, CELL_Z + k};
            case 1 -> new int[]{CELL_X + SPAN_CELLS, CELL_Z + k};
            case 2 -> new int[]{CELL_X + k, CELL_Z - 1};
            default -> new int[]{CELL_X + k, CELL_Z + SPAN_CELLS};
        };
    }

    /**
     * Every cell you can reach from a Glade door with the camp sealed.
     *
     * <p>The whole point of asking: a breach onto a pocket of maze nobody can
     * get to is a camp nobody can enter. The old code picked breaches by hash
     * alone, and on one of the seven presets every single one of them opened
     * onto unreachable corridor - the camp would simply have been sealed for a
     * day, with nothing to say why.
     */
    private static boolean[] reachableFromDoors(MazeData.Layout layout) {
        boolean[] seen = new boolean[MazeData.GRID * MazeData.GRID];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int[] door : MazeBuilder.DOOR_CELLS) {
            seen[door[1] * MazeData.GRID + door[0]] = true;
            queue.add(new int[]{door[0], door[1]});
        }
        int[][] steps = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int nz = at[1] + step[1];
                if (nx < 0 || nz < 0 || nx >= MazeData.GRID || nz >= MazeData.GRID) {
                    continue;
                }
                if (seen[nz * MazeData.GRID + nx] || MazeData.inGlade(nx, nz)
                        || coversCell(nx, nz)) {
                    continue;
                }
                if (!MazeData.isOpen(at[0], at[1], nx, nz, layout)) {
                    continue;
                }
                seen[nz * MazeData.GRID + nx] = true;
                queue.add(new int[]{nx, nz});
            }
        }
        return seen;
    }

    /**
     * Tonight's ways in, chosen from the breaches that actually go somewhere.
     *
     * <p>Deterministic from the layout's own name, so the same preset always
     * opens the same wall and a veteran can genuinely learn "on day four you go
     * in from the east". Across the seven presets no two share a set, so the
     * approach really does move.
     */
    public static java.util.List<Integer> breachesFor(MazeData.Layout layout) {
        java.util.List<Integer> usable = new java.util.ArrayList<>();
        boolean[] reach = reachableFromDoors(layout);
        for (int i = 0; i < CANDIDATES; i++) {
            int[] out = candidateOutside(i);
            if (out[0] < 0 || out[1] < 0 || out[0] >= MazeData.GRID || out[1] >= MazeData.GRID) {
                continue;
            }
            if (reach[out[1] * MazeData.GRID + out[0]]) {
                usable.add(i);
            }
        }
        java.util.List<Integer> picked = new java.util.ArrayList<>();
        if (usable.isEmpty()) {
            return picked;
        }
        // Spread across distinct faces before doubling up on one. The north
        // face has far more reachable candidates than the others - usually ten
        // of ten against nought-to-four - so a flat pick landed all three
        // breaches on the same wall on some presets, and "the way in moves"
        // quietly became "you always come in from the north".
        java.util.List<Integer> faces = new java.util.ArrayList<>();
        for (int i : usable) {
            int f = i / PER_SIDE;
            if (!faces.contains(f)) {
                faces.add(f);
            }
        }
        int h = layout.name().hashCode() & 0x7FFFFFFF;
        for (int n = 0; n < BREACHES; n++) {
            int face = faces.get((h + n) % faces.size());
            java.util.List<Integer> onFace = new java.util.ArrayList<>();
            for (int i : usable) {
                if (i / PER_SIDE == face && !picked.contains(i)) {
                    onFace.add(i);
                }
            }
            if (onFace.isEmpty()) {
                continue;
            }
            picked.add(onFace.get((h + n * 3) % onFace.size()));
        }
        return picked;
    }

    /**
     * Seals the wall and cuts tonight's holes in it.
     *
     * <p>Run with the nightly reshape, so the camp stays put while the maze
     * rearranges itself around it and the way in is somewhere else in the
     * morning.
     */
    public static void applyBreaches(ServerLevel level, MazeData.Layout layout) {
        if (layout == null) {
            return;
        }
        RandomSource rng = RandomSource.create(0xDEAD_BEEFL + layout.name().hashCode());
        wallRing(level, rng);
        java.util.List<Integer> open = breachesFor(layout);
        if (open.isEmpty()) {
            // Cannot happen with the seven shipped presets - all of them leave
            // at least four usable - but a future preset could seal the camp
            // off entirely, and a landmark nobody can enter should say so
            // rather than just being missing.
            for (ServerPlayer p : level.players()) {
                if (p.hasPermissions(2)) {
                    p.displayClientMessage(Component.literal(
                            "§e⚠ " + layout.name() + ": no reachable way into the Dead Glade."), false);
                }
            }
            return;
        }
        for (int i : open) {
            cutBreach(level, rng, i);
        }
    }

    /** One collapsed section, corridor-wide and head-high, with the rubble left. */
    private static void cutBreach(ServerLevel level, RandomSource rng, int i) {
        int k = along(i);
        int s = side(i);
        int lo = MazeData.corridorMin(s < 2 ? CELL_Z + k : CELL_X + k);
        int hi = MazeData.corridorMax(s < 2 ? CELL_Z + k : CELL_X + k);
        int fixed = switch (s) {
            case 0 -> minBlock();
            case 1 -> maxBlock();
            case 2 -> minBlockZ();
            default -> maxBlockZ();
        };
        for (int a = lo; a <= hi; a++) {
            for (int y = Y + 1; y <= Y + 4; y++) {
                BlockPos at = s < 2 ? new BlockPos(fixed, y, a) : new BlockPos(a, y, fixed);
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            }
            // A ragged top edge, so it reads as fallen rather than as a doorway
            // somebody cut.
            if (rng.nextInt(2) == 0) {
                BlockPos lip = s < 2 ? new BlockPos(fixed, Y + 5, a) : new BlockPos(a, Y + 5, fixed);
                level.setBlock(lip, Blocks.AIR.defaultBlockState(), 2);
            }
            BlockPos floor = s < 2 ? new BlockPos(fixed, Y + 1, a) : new BlockPos(a, Y + 1, fixed);
            if (rng.nextInt(3) == 0) {
                level.setBlock(floor, Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState(), 2);
            }
        }
    }

    // ------------------------------------------------------------------
    // The charts
    // ------------------------------------------------------------------

    /**
     * Reading their survey.
     *
     * <p>Marks everything within {@link #CHART_RADIUS} cells of their camp onto
     * the Glade's chart - a large region your Runners would otherwise spend days
     * walking, handed over in one go. That is the reason to come here, and it is
     * deliberately information rather than loot: a chest of iron would make this
     * a supply run, and the one thing this place should change is what the Glade
     * <em>knows</em>.
     *
     * <p>Once per game. Their notes do not get better on a second reading.
     */
    public static boolean readCharts(ServerLevel level, ServerPlayer who) {
        if (level.getServer() == null) {
            return false;
        }
        MazeCharts charts = MazeCharts.get(level.getServer());
        MazeData.Layout layout = MazeRuntime.todaysLayout(level);
        String name = layout == null ? null : layout.name();
        int cellX = centreX() / MazeData.CELL;
        int cellZ = centreZ() / MazeData.CELL;

        int fresh = 0;
        for (int dx = -CHART_RADIUS; dx <= CHART_RADIUS; dx++) {
            for (int dz = -CHART_RADIUS; dz <= CHART_RADIUS; dz++) {
                int x = cellX + dx;
                int z = cellZ + dz;
                if (x < 0 || z < 0 || x >= MazeData.GRID || z >= MazeData.GRID) {
                    continue;
                }
                if (MazeData.inGlade(x, z)) {
                    continue;
                }
                if (charts.chart(who.getUUID(), x, z, name)) {
                    fresh++;
                }
            }
        }
        if (fresh == 0) {
            who.displayClientMessage(Component.literal(
                    "§7You have already read these. §8Nothing here you do not know."), true);
            return false;
        }
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§6§l✦ THEIR CHARTS §r§7— §f" + who.getGameProfile().getName()
                            + "§7 read the Dead Glade's survey. §f" + fresh
                            + "§7 cells added to the chart."), false);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 1.0F, 1.3F);
        }
        // Charting is a Runner's trade, so it pays a Runner's day.
        MazeDayWork.get(level).add(level, who, MazeJobs.RUNNER, Math.min(fresh, 40));
        return true;
    }

    // ------------------------------------------------------------------

    private static void fill(ServerLevel level, BlockPos at, ItemStack... items) {
        BlockEntity be = level.getBlockEntity(at);
        if (!(be instanceof Container box)) {
            return;
        }
        for (int i = 0; i < items.length && i < box.getContainerSize(); i++) {
            box.setItem(i, items[i]);
        }
    }

    private static void sign(ServerLevel level, BlockPos pos, Direction facing,
                             String a, String b, String c, String d) {
        level.setBlock(pos, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            be.updateText(t -> t.setMessage(0, Component.literal(a))
                    .setMessage(1, Component.literal(b))
                    .setMessage(2, Component.literal(c))
                    .setMessage(3, Component.literal(d)), true);
        }
    }
}
