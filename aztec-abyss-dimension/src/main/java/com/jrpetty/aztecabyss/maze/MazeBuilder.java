package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /**
     * Cells stamped per tick. Lowered as the walls gained detail: each cell now
     * writes noticeably more blocks, and a longer build is far better than a
     * shorter one that stutters the server while it runs.
     */
    private static final int CELLS_PER_TICK = 24;

    private static final BlockState FLOOR = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_WORN = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_MOSS = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState GLADE_GROUND = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

    /**
     * Height of the invisible lid over the maze.
     *
     * <p>Four clear blocks above the wall tops. A Griever climbs a wall and walks
     * along the top of it exactly as it always could; a runner cannot reach the
     * top of an eighteen-block wall at all, and now cannot get above one by any
     * other means either. The Glade is deliberately left open to the sky - it is
     * the one place on the map you are meant to be able to look up from.
     */
    // One block above the wall tops, not four. The dimension is 80 blocks tall
    // from y=0, so the highest writable block is 79 - and the old value of 82 was
    // simply outside the world. Every setBlock for the lid silently did nothing,
    // which is why the maze had no roof at all despite the code saying it did.
    private static final int SKY_LID_Y = MazeData.WALL_TOP_Y + 1;

    /**
     * One colour per compass section, banded into the corridor walls.
     *
     * <p>This is the answer to the one gap the handoff calls out by name: nothing
     * in the maze rewarded charting it. Identical grey corridors in every
     * direction make a map you cannot hold in your head. A colour per section
     * means "I came in through the green" is a real sentence, and a Runner's
     * account of a route becomes worth listening to.
     */
    private static final BlockState[] SECTION_COLOURS = {
            Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.LIME_TERRACOTTA.defaultBlockState(),
            Blocks.CYAN_TERRACOTTA.defaultBlockState(),
            Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState(),
            Blocks.PURPLE_TERRACOTTA.defaultBlockState(),
            Blocks.MAGENTA_TERRACOTTA.defaultBlockState(),
    };

    /** Cursor into the 96x96 cell grid; -1 means no build in progress. */
    private static int cursor = -1;

    private MazeBuilder() {
    }

    /** True once the maze has been stamped in this world. */
    /**
     * Where the "this world already has a maze" marker lives.
     *
     * <p>Down at the very bottom of the dimension, nowhere near anything the
     * builders touch. It used to be inferred from scenery instead - a grass
     * block at the spawn point - and that was wrong in the worst possible way:
     * {@link GladeBuilder} lays a worn <em>path</em> across the middle of the
     * Glade and then drops the Box on top of it, so the spawn block ends up
     * deepslate and the check could never come back true.
     *
     * <p>The consequence was that the maze could never be entered at all. Every
     * attempt called {@code beginIfNeeded}, saw "not built", restarted the build
     * from zero, and was turned away because a build was running - forever. A
     * marker nothing else writes to cannot fail that way.
     */
    private static final BlockPos BUILT_MARKER = new BlockPos(MazeData.SPAWN_X, 1, MazeData.SPAWN_Z);

    /**
     * A second marker recording which <em>version</em> of the geometry is down.
     *
     * <p>The built-marker alone answers "has this world ever had a maze", which is
     * the wrong question the moment the shape of the map changes in code. A world
     * built before the Glade ring was sealed, or before the sky lid existed, is
     * built - and wrong, silently, with no way for a player to know that the map
     * they are running is a version behind the rules they were told.
     *
     * <p>So the marker encodes a number. When the geometry changes here, the
     * constant below goes up, and every existing world restamps itself the next
     * time anyone walks in rather than waiting to be told to.
     */
    private static final BlockPos VERSION_MARKER = new BlockPos(MazeData.SPAWN_X, 2, MazeData.SPAWN_Z);

    /**
     * Bumped whenever the stamped shape of the map changes.
     *
     * <p>2: sealed the lap around the outside of the Glade, dressed the Glade wall,
     * and put the barrier lid over the corridors.
     * <p>3: added the Map Room to the Glade.
     * <p>4: added the Job Board to the Glade.
     * <p>5: the sky lid moved inside the world's height limit so it actually
     * exists, the seven portal annexes were built outside the square, and the
     * Griever holes were dug.
     * <p>6: the Chart Floor was laid, and the homestead moved east to make room.
     * <p>7: the huts got pitched roofs and interiors, the field got a fence all
     * the way round with a gate, the Deadheads became graves, and nothing
     * scattered plants itself through a building any more.
     * <p>8: the Dead Glade was carved into the south-west.
     * <p>9: the bell tower went up in the Glade.
     */
    private static final int GEOMETRY_VERSION = 9;

    /** One distinctive block per version, so the marker is readable in-world. */
    private static final BlockState[] VERSION_BLOCKS = {
            Blocks.BEDROCK.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.DEEPSLATE_TILES.defaultBlockState(),
            Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
            Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
            // One per version, and the list has to grow with it: with seven
            // entries, version 7 wrapped back onto version 0's bedrock and the
            // marker stopped being able to tell those two apart.
            Blocks.SMOOTH_BASALT.defaultBlockState(),
            Blocks.TUFF.defaultBlockState(),
            Blocks.CALCITE.defaultBlockState(),
    };

    private static BlockState versionBlock() {
        return VERSION_BLOCKS[Math.floorMod(GEOMETRY_VERSION, VERSION_BLOCKS.length)];
    }

    public static boolean isBuilt(ServerLevel level) {
        return level.getBlockState(BUILT_MARKER).is(Blocks.BEDROCK)
                && level.getBlockState(VERSION_MARKER).is(versionBlock().getBlock());
    }

    public static boolean isBuilding() {
        return cursor >= 0;
    }

    /**
     * Forces a full restamp of an already-built maze.
     *
     * <p>Needed whenever the maze's geometry changes in code - corridor width, the
     * Glade wall, anything structural. A world that already has a maze will never
     * be restamped on its own, so without this the only way to see a change to the
     * shape of the map is to start a new world.
     */
    public static void forceRebuild(ServerLevel level) {
        level.setBlock(BUILT_MARKER, Blocks.AIR.defaultBlockState(), 2);
        cursor = 0;
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
        return (int) (cursor * 100L / total());
    }

    /** Cells, then the annexes hanging off the outside, then the Griever holes. */
    private static int total() {
        return MazeData.GRID * MazeData.GRID + PortalAnnex.SLICES + GrieverHoles.SLICES;
    }

    /**
     * Advances a running build by one slice. Safe to call every tick; does
     * nothing once the map is down.
     */
    public static void tick(ServerLevel level) {
        if (cursor < 0) {
            return;
        }
        int cells = MazeData.GRID * MazeData.GRID;
        if (cursor < cells) {
            int end = Math.min(cursor + CELLS_PER_TICK, cells);
            RandomSource rng = RandomSource.create();
            for (int i = cursor; i < end; i++) {
                stampCell(level, i % MazeData.GRID, i / MazeData.GRID, rng);
            }
            cursor = end;
            return;
        }
        // The annexes and the holes are stamped a row at a time off the same
        // cursor. Doing them in finish() would be seven hundred columns and
        // eight shafts on a single tick, which is a visible hitch on the exact
        // tick a player is waiting to be let in.
        int i = cursor - cells;
        if (i < PortalAnnex.SLICES) {
            PortalAnnex.stampSlice(level, i);
        } else {
            GrieverHoles.stampSlice(level, i - PortalAnnex.SLICES);
        }
        cursor++;
        if (cursor >= total()) {
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
                // The lid. Written here rather than in its own pass because this
                // loop already visits every column in the map, so it costs one
                // more block per column and inherits the staging for nothing.
                if (!glade) {
                    level.setBlock(new BlockPos(x, SKY_LID_Y, z), BARRIER, 2);
                }

                if (glade || isCorridor(lx, lz, openW, openE, openN, openS)) {
                    continue;
                }
                for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                    level.setBlock(new BlockPos(x, y, z), wallBlock(x, y, z), 2);
                }
            }
        }
        if (!glade) {
            corridorFloor(level, cx, cz, openW, openE, openN, openS);
            ivy(level, cx, cz, openW, openE, openN, openS);
            landmark(level, cx, cz, openW, openE, openN, openS);
        }
    }

    /**
     * The face of the maze, and the single thing that decides whether it looks
     * like a set or like a box.
     *
     * <p>Height does most of the work. The bottom courses are grimy and wet where
     * the walls meet the ground, the middle is the weathered brick you spend the
     * run staring at, and the top lightens so the walls read as tall from below.
     * Vertical ribs every few blocks break up the flat, and everything is keyed
     * off the block's own coordinates so the pattern is stable rather than
     * static-noise - a wall you have seen before looks the way you remember it.
     */
    private static BlockState wallBlock(int x, int y, int z) {
        int h = Math.floorMod(x * 73856093 ^ z * 19349663 ^ y * 83492791, 100);
        int fromBase = y - MazeData.WALL_BASE_Y;
        int height = MazeData.WALL_TOP_Y - MazeData.WALL_BASE_Y;

        // Ribs: full-height pilasters every fifth block along the wall.
        if (Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0) {
            return fromBase > height - 2
                    ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                    : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        // Cap course, so the tops silhouette instead of ending flat.
        if (fromBase >= height - 1) {
            return h < 40 ? Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState()
                    : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        }
        // Damp, dirty footings.
        if (fromBase <= 2) {
            if (h < 30) {
                return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            }
            return h < 55 ? Blocks.COBBLESTONE.defaultBlockState()
                    : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        // Upper courses lighten, which makes the wall read as taller than it is.
        if (fromBase > height * 2 / 3) {
            return h < 12 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : h < 20 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState();
        }
        // The body of the wall.
        if (h < 18) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        if (h < 32) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (h < 36) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    /** Corridor underfoot: worn flags, grit, and moss creeping out of the joints. */
    private static void corridorFloor(ServerLevel level, int cx, int cz,
                                      boolean openW, boolean openE, boolean openN, boolean openS) {
        for (int lx = 0; lx < MazeData.CELL; lx++) {
            for (int lz = 0; lz < MazeData.CELL; lz++) {
                if (!isCorridor(lx, lz, openW, openE, openN, openS)) {
                    continue;
                }
                int x = cx * MazeData.CELL + lx;
                int z = cz * MazeData.CELL + lz;
                int h = Math.floorMod(x * 40503 ^ z * 26861, 100);
                BlockState floor = h < 12 ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : h < 22 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                        : h < 32 ? Blocks.COBBLESTONE.defaultBlockState()
                        : h < 45 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                level.setBlock(new BlockPos(x, MazeData.FLOOR_Y, z), floor, 2);
                if (h < 5) {
                    level.setBlock(new BlockPos(x, MazeData.FLOOR_Y + 1, z),
                            Blocks.MOSS_CARPET.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Ivy down the corridor walls - the single cheapest thing that stops the maze
     * reading as a stone box. Hung from the wall face into the corridor air, so
     * it only ever appears where somebody can actually see it.
     */
    private static void ivy(ServerLevel level, int cx, int cz,
                            boolean openW, boolean openE, boolean openN, boolean openS) {
        for (int across = MazeData.CORRIDOR_MIN; across <= MazeData.CORRIDOR_MAX; across++) {
            hangIvy(level, cx, cz, across, !openN, Direction.NORTH);
            hangIvy(level, cx, cz, across, !openS, Direction.SOUTH);
            hangIvy(level, cx, cz, across, !openW, Direction.WEST);
            hangIvy(level, cx, cz, across, !openE, Direction.EAST);
        }
    }

    private static void hangIvy(ServerLevel level, int cx, int cz, int across,
                                boolean hasWall, Direction wallSide) {
        if (!hasWall) {
            return;
        }
        boolean alongX = wallSide.getAxis() == Direction.Axis.Z;
        int inner = wallSide == Direction.NORTH || wallSide == Direction.WEST
                ? MazeData.CORRIDOR_MIN : MazeData.CORRIDOR_MAX;
        int x = cx * MazeData.CELL + (alongX ? across : inner);
        int z = cz * MazeData.CELL + (alongX ? inner : across);

        int h = Math.floorMod(x * 15485863 ^ z * 32452843, 100);
        if (h > 34) {
            return;
        }
        // Vines attach to the face they are grown against, so the boolean is the
        // side the wall is on - the opposite of the direction they hang toward.
        BlockState vine = Blocks.VINE.defaultBlockState()
                .setValue(sideProperty(wallSide), true);
        int len = 2 + h % 5;
        for (int i = 0; i < len; i++) {
            BlockPos at = new BlockPos(x, MazeData.WALL_TOP_Y - 1 - i, z);
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, vine, 2);
            }
        }
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty sideProperty(Direction d) {
        return switch (d) {
            case NORTH -> net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH;
            case SOUTH -> net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;
            case WEST -> net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;
            default -> net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST;
        };
    }

    /** Which of the eight compass sections a cell belongs to. */
    public static int sectionOf(int cx, int cz) {
        int mid = MazeData.GRID / 2;
        double angle = Math.atan2(cz - mid, cx - mid) + Math.PI;
        return (int) Math.floor(angle / (Math.PI * 2.0) * 8.0) % 8;
    }

    /**
     * Marks a cell with its section colour and, occasionally, something to
     * remember it by.
     *
     * <p>Deterministic from the cell itself rather than from a running RNG, so
     * the same corridor is marked the same way on every server and a route
     * described by one player is followable by another.
     */
    private static void landmark(ServerLevel level, int cx, int cz,
                                 boolean openW, boolean openE, boolean openN, boolean openS) {
        int hash = Math.abs((cx * 73856093) ^ (cz * 19349663));
        if (hash % 5 != 0) {
            return;
        }
        BlockState colour = SECTION_COLOURS[sectionOf(cx, cz)];
        int y = MazeData.WALL_BASE_Y + 2;

        // A band set into whichever wall the corridor actually runs past, so it
        // is always facing someone walking through.
        for (int across = MazeData.CORRIDOR_MIN; across <= MazeData.CORRIDOR_MAX; across++) {
            if (!openN) {
                level.setBlock(new BlockPos(cx * MazeData.CELL + across, y,
                        cz * MazeData.CELL + MazeData.CORRIDOR_MIN - 1), colour, 2);
            }
            if (!openS) {
                level.setBlock(new BlockPos(cx * MazeData.CELL + across, y,
                        cz * MazeData.CELL + MazeData.CORRIDOR_MAX + 1), colour, 2);
            }
            if (!openW) {
                level.setBlock(new BlockPos(cx * MazeData.CELL + MazeData.CORRIDOR_MIN - 1, y,
                        cz * MazeData.CELL + across), colour, 2);
            }
            if (!openE) {
                level.setBlock(new BlockPos(cx * MazeData.CELL + MazeData.CORRIDOR_MAX + 1, y,
                        cz * MazeData.CELL + across), colour, 2);
            }
        }

        // One in five of those gets something with a silhouette, so a junction is
        // recognisable from down the corridor and not just up close.
        if (hash % 25 == 0) {
            BlockPos centre = new BlockPos(cx * MazeData.CELL + MazeData.CORRIDOR_MIN,
                    MazeData.FLOOR_Y + 1, cz * MazeData.CELL + MazeData.CORRIDOR_MIN);
            int kind = (hash / 25) % 4;
            switch (kind) {
                case 0 -> level.setBlock(centre, Blocks.CHAIN.defaultBlockState(), 2);
                case 1 -> level.setBlock(centre.above(2), Blocks.LANTERN.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true), 2);
                case 2 -> level.setBlock(centre, Blocks.VINE.defaultBlockState(), 2);
                default -> level.setBlock(centre, Blocks.COBWEB.defaultBlockState(), 2);
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

    /** Seals the outer rim, then dresses the Glade. */
    private static void finish(ServerLevel level) {
        GladeBuilder.build(level);
        // Somebody else's clearing, carved out of the corridors after they are
        // stamped so it eats the walls rather than being drawn over them.
        DeadGlade.build(level);
        gladeWall(level);
        // After the wall, because it writes the cells the wall stands between.
        sealGladeRing(level);
        // Stamp the markers last, so a build interrupted by a crash or a restart
        // is treated as unbuilt and simply runs again.
        level.setBlock(VERSION_MARKER, versionBlock(), 2);
        level.setBlock(BUILT_MARKER, BEDROCK, 2);
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
    /** The four cells the Glade's doors sit in, in build order N, E, S, W. */
    public static final int[][] DOOR_CELLS = {{48, 39}, {56, 48}, {47, 56}, {39, 47}};

    /**
     * Walls the Glade in, leaving only the four doors.
     *
     * <p>Without this the Glade simply ended and the maze started, so wherever a
     * bordering cell happened to have a corridor along that edge you could step
     * straight out of the clearing - and, worse, walk the whole way round the
     * outside of it. The doors were four openings in a fence that was not there.
     *
     * <p>Built as a solid ring one cell outside the Glade proper, punched through
     * at the four door cells and nowhere else.
     */
    public static void gladeWall(ServerLevel level) {
        int lo = MazeData.GLADE_MIN_CELL * MazeData.CELL - 1;
        int hi = (MazeData.GLADE_MAX_CELL + 1) * MazeData.CELL;
        RandomSource rng = RandomSource.create(0x6AD3);
        for (int i = lo; i <= hi; i++) {
            for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                BlockState s = gladeStone(i, y, rng);
                level.setBlock(new BlockPos(i, y, lo), s, 2);
                level.setBlock(new BlockPos(i, y, hi), gladeStone(i, y, rng), 2);
                level.setBlock(new BlockPos(lo, y, i), gladeStone(i, y, rng), 2);
                level.setBlock(new BlockPos(hi, y, i), gladeStone(i, y, rng), 2);
            }
        }
        gladeWallDressing(level, lo, hi);
        // Punch the four doors back through, full corridor width and head height.
        for (int[] cell : DOOR_CELLS) {
            int bx = cell[0] * MazeData.CELL;
            int bz = cell[1] * MazeData.CELL;
            for (int a = MazeData.CORRIDOR_MIN; a <= MazeData.CORRIDOR_MAX; a++) {
                for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_BASE_Y + 5; y++) {
                    // Clear the ring wherever it crosses this door cell, on both
                    // axes - one of the two is the ring, the other is a no-op.
                    for (int s = -1; s <= MazeData.CELL; s++) {
                        int x = bx + a;
                        int z = bz + s;
                        if (z == lo || z == hi) {
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                        x = bx + s;
                        z = bz + a;
                        if (x == lo || x == hi) {
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    /**
     * The face of the Glade wall: courses, not rubble.
     *
     * <p>The maze's own walls are deliberately monotonous - that is what makes
     * them a maze. This one is the opposite thing and has to look it, because it
     * is the first structure anybody sees and the only one they will stand and
     * look at. It is the boundary of the only safe ground on the map, so it should
     * read as something that was built to keep a thing out.
     *
     * <p>Three devices, all cheap: a dark plinth at the bottom so it sits on the
     * ground rather than starting from it, a banded course at eye height so the
     * height is legible, and buttresses every eight blocks in a darker stone.
     */
    private static BlockState gladeStone(int along, int y, RandomSource rng) {
        int fromBase = y - MazeData.WALL_BASE_Y;
        int height = MazeData.WALL_TOP_Y - MazeData.WALL_BASE_Y;
        if (fromBase <= 1) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        if (fromBase == height) {
            // Crenellation: the top course alternates so the skyline is toothed
            // rather than a flat line drawn across the sky.
            return (along & 1) == 0
                    ? Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState()
                    : Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        }
        if (fromBase == height - 1) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (along % 8 == 0) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState(); // buttress
        }
        if (fromBase == 5) {
            return Blocks.CHISELED_STONE_BRICKS.defaultBlockState(); // the band
        }
        return wallStone(rng);
    }

    /**
     * Lanterns on the inside face, so the Glade has a lit boundary after dark.
     *
     * <p>A wall you cannot see at night is a wall you walk into. Lighting only the
     * inward face keeps the corridors beyond it as black as they were.
     */
    private static void gladeWallDressing(ServerLevel level, int lo, int hi) {
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (int i = lo + 4; i < hi; i += 8) {
            int y = MazeData.WALL_BASE_Y + 4;
            trySet(level, new BlockPos(i, y, lo + 1), lantern);
            trySet(level, new BlockPos(i, y, hi - 1), lantern);
            trySet(level, new BlockPos(lo + 1, y, i), lantern);
            trySet(level, new BlockPos(hi - 1, y, i), lantern);
        }
    }

    /** Places only into air, so dressing can never eat the wall it decorates. */
    private static void trySet(ServerLevel level, BlockPos at, BlockState state) {
        if (level.getBlockState(at).isAir()) {
            level.setBlock(at, state, 2);
        }
    }

    /**
     * Cuts the lap that ran the whole way round the outside of the Glade.
     *
     * <p>The Glade wall stopped you stepping out of the clearing sideways, but the
     * ring of cells immediately outside it was still a continuous corridor. So all
     * four doors opened onto the same lane: pick any door, turn, and walk round to
     * whichever of the other three you actually wanted. Four doors that share one
     * corridor are one door with four names, and the choice of which to take -
     * which is the only decision the Glade offers - meant nothing at all.
     *
     * <p>Sealing every edge between two ring cells breaks the lap without touching
     * anything else: each door can still only go outward, and outward is where the
     * carved routes go.
     */
    public static void sealGladeRing(ServerLevel level) {
        RandomSource rng = RandomSource.create(0x5EA1);
        int lo = MazeData.GLADE_MIN_CELL - 1;
        int hi = MazeData.GLADE_MAX_CELL + 1;
        for (int cx = lo; cx <= hi; cx++) {
            for (int cz = lo; cz <= hi; cz++) {
                if (!onRing(cx, cz, lo, hi)) {
                    continue;
                }
                // East and south only; the neighbour's own pass covers the others,
                // and doing all four would write every edge twice.
                if (onRing(cx + 1, cz, lo, hi)) {
                    writeHalf(level, cx, cz, cx + 1, cz, false, rng);
                    writeHalf(level, cx + 1, cz, cx, cz, false, rng);
                }
                if (onRing(cx, cz + 1, lo, hi)) {
                    writeHalf(level, cx, cz, cx, cz + 1, false, rng);
                    writeHalf(level, cx, cz + 1, cx, cz, false, rng);
                }
            }
        }
    }

    /** A cell on the square ring of cells that hugs the Glade. */
    private static boolean onRing(int cx, int cz, int lo, int hi) {
        if (cx < lo || cx > hi || cz < lo || cz > hi) {
            return false;
        }
        return cx == lo || cx == hi || cz == lo || cz == hi;
    }

    /** Opens the wall between two neighbouring cells, both halves. */
    public static void openEdge(ServerLevel level, int cx, int cz, int nx, int nz, RandomSource rng) {
        writeHalf(level, cx, cz, nx, nz, true, rng);
        writeHalf(level, nx, nz, cx, cz, true, rng);
    }

    /**
     * Carves a guaranteed corridor from one cell to another.
     *
     * <p>This is the promise that there is always a way out. Rather than trusting
     * the dataset's connectivity to happen to join a door to today's exit - which
     * nothing checks, and which would strand a runner in a maze with no solution -
     * a route is cut by construction: step the cell cursor toward the target one
     * cell at a time, opening every wall it crosses on the way.
     *
     * <p>The walk is deliberately not a straight line. It picks its axis from a
     * position hash, so each door's route wanders differently and the four of them
     * do not become the same corridor the moment they leave the clearing.
     *
     * <p>Glade cells are never carved through - a route that cut across the
     * clearing would be a hole in the wall we just built.
     */
    public static void carveRoute(ServerLevel level, int fromX, int fromZ,
                                  int toX, int toZ, int salt) {
        RandomSource rng = RandomSource.create();
        int cx = fromX;
        int cz = fromZ;
        int guard = 0;
        // The cell grid is 96 a side; a Manhattan walk cannot need more steps
        // than the perimeter, and the guard stops a pathological loop dead.
        while ((cx != toX || cz != toZ) && guard++ < MazeData.GRID * 4) {
            int dx = Integer.signum(toX - cx);
            int dz = Integer.signum(toZ - cz);
            boolean stepX;
            if (dx == 0) {
                stepX = false;
            } else if (dz == 0) {
                stepX = true;
            } else {
                // Hashed choice: same maze every time, different per door.
                stepX = com.jrpetty.aztecabyss.worldgen.Deco.hashUnit(cx, salt, cz, salt) < 0.5f;
            }
            int nx = stepX ? cx + dx : cx;
            int nz = stepX ? cz : cz + dz;
            // The whole grid, rim included. This used to stop one cell short of
            // the edge, and every exit in the dataset sits on the edge - cell 0
            // or cell 95 - so the walk could never once reach its destination.
            // A route that refuses to enter the only cells it is ever aimed at
            // is not a route.
            if (nx < 0 || nz < 0 || nx >= MazeData.GRID || nz >= MazeData.GRID) {
                break;
            }
            if (MazeData.inGlade(nx, nz)) {
                // Never tunnel back through the clearing; sidestep instead.
                if (stepX && dz != 0) {
                    nx = cx;
                    nz = cz + dz;
                } else if (!stepX && dx != 0) {
                    nx = cx + dx;
                    nz = cz;
                } else {
                    break;
                }
                if (MazeData.inGlade(nx, nz)) {
                    break;
                }
            }
            openEdge(level, cx, cz, nx, nz, rng);
            cx = nx;
            cz = nz;
        }
    }

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
