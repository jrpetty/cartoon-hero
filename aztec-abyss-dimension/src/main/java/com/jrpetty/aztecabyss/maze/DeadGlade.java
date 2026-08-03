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
