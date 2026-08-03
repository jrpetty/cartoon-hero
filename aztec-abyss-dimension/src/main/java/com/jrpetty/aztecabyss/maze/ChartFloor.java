package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * The Chart Floor: the maze, seen from above, revealed only where somebody has
 * actually been.
 *
 * <p>Charting existed and had nowhere to go. It lived in a SavedData and could be
 * printed into chat by a command, which is a debug readout rather than a map -
 * you cannot stand round a chat message with four other people and argue about
 * which way the south passage bends.
 *
 * <p>So the Glade gets a floor you walk on. It starts black. The only thing
 * visible on day one is the Glade's own square in the middle, because that is the
 * only ground anybody has stood on. Everything else fills in <em>live</em> as
 * Runners move: a corridor somebody ran down appears as a line, a dead end
 * appears as a stub, and the shape of the maze assembles itself out of other
 * people's legs over the course of a week.
 *
 * <h2>It only ever shows what was walked</h2>
 *
 * <p>Nothing here reads the world. The floor cannot draw a corridor nobody has
 * been down, which is the entire point - a fog-of-war map that quietly knows the
 * answer is a minimap, and a minimap makes the Runners pointless. The only way a
 * lane appears on this floor is that somebody walked it and came back.
 *
 * <h2>One chart per layout, labelled by nothing</h2>
 *
 * <p>The maze rearranges nightly between seven presets, so a route is only true
 * on the layout it was found on. The floor keeps them apart and the dial cycles
 * between them - but they are labelled <b>Chart I, II, III</b> in the order the
 * Glade met them, never by which day they are. Working out that Chart IV is the
 * one that comes back every seventh night is a thing the Glade has to do for
 * itself, and it is the best possible use of a map room.
 */
public final class ChartFloor {

    /** The mosaic is this many blocks on a side. */
    public static final int SIZE = 42;

    /** North-west corner of the mosaic, one block inside the Glade. */
    public static int originX() {
        return MazeData.gladeMinBlock() + 1;
    }

    public static int originZ() {
        return MazeData.gladeMinBlock() + 1;
    }

    /** The mosaic is laid at ground level, so it is walked on rather than viewed. */
    private static final int Y = MazeData.FLOOR_Y;
    /** How often the whole thing is repainted regardless of the diff. */
    private static final long FULL_REPAINT_TICKS = 1200L;

    /** Which chart the floor is currently showing, as an index into discovery order. */
    private static int showing = 0;
    /** What is currently drawn, so a refresh only writes what changed. */
    private static byte[] drawn = null;

    /**
     * How much of the maze the forty-two blocks are showing.
     *
     * <p>The chart used to be one fixed view: ninety-six cells of maze squeezed
     * into forty-two blocks, so a single tile covered two and a bit cells and a
     * wall was physically unrepresentable. It could tell you <em>that</em> a
     * region had been walked and never once <em>what was in it</em> - which is
     * the only thing a maze map is for.
     *
     * <p>So it zooms. The whole map for orientation, a quarter for planning, and
     * a close view at three tiles to the cell - which is the resolution at which
     * corridors and walls become separate things and the floor stops being a
     * heat map and starts being a map.
     */
    private static final int[] ZOOM_CELLS = {96, 48, 14};
    private static final String[] ZOOM_NAME = {"the whole maze", "a quarter", "close"};
    private static int zoom = 0;

    /** Cell the close view is centred on, per the last person to walk out there. */
    private static int focusX = MazeData.GRID / 2;
    private static int focusZ = MazeData.GRID / 2;

    private ChartFloor() {
    }

    public static void reset() {
        drawn = null;
        showing = 0;
        zoom = 0;
        focusX = MazeData.GRID / 2;
        focusZ = MazeData.GRID / 2;
    }

    /** The second stone: how far in the chart is drawn. */
    public static BlockPos zoomDial() {
        return new BlockPos(originX() + SIZE / 2 - 3, Y + 1, originZ() + SIZE + 1);
    }

    /**
     * Remembers where somebody actually was, so the close view has a subject.
     *
     * <p>Called from the runtime as people walk. A close map centred on the
     * middle of the grid would be centred on the Glade, which is the one part
     * of this world nobody needs a map of.
     */
    public static void focusOn(int cellX, int cellZ) {
        if (MazeData.inGlade(cellX, cellZ)) {
            return;
        }
        focusX = cellX;
        focusZ = cellZ;
    }

    /** The block you punch to change chart. */
    public static BlockPos dial() {
        return new BlockPos(originX() + SIZE / 2, Y + 1, originZ() + SIZE + 1);
    }

    // ------------------------------------------------------------------
    // Building it
    // ------------------------------------------------------------------

    /**
     * Lays the plaza: a kerbed bed flush with the Glade, and the dial on its
     * south edge.
     *
     * <p>Flush rather than raised, so you walk out onto the map and stand on the
     * part of the maze you are talking about. A map on a wall is a thing one
     * person reads; a map you are standing in the middle of is a thing five
     * people argue over, and the argument is the point. The slab kerb is only
     * there so the edge of the map is unambiguous.
     */
    public static void build(ServerLevel level) {
        int ox = originX();
        int oz = originZ();
        for (int x = ox - 1; x <= ox + SIZE; x++) {
            for (int z = oz - 1; z <= oz + SIZE; z++) {
                boolean rim = x == ox - 1 || z == oz - 1 || x == ox + SIZE || z == oz + SIZE;
                if (rim) {
                    level.setBlock(new BlockPos(x, Y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, Y + 1, z),
                            Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState(), 2);
                    continue;
                }
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y + 1, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Corner lamps, so it is legible at night. This is where the Glade will
        // be standing for most of the dark.
        for (int cx = 0; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                BlockPos post = new BlockPos(ox - 1 + cx * (SIZE + 1), Y + 1, oz - 1 + cz * (SIZE + 1));
                level.setBlock(post, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 2);
                level.setBlock(post.above(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        }
        // The dial: one block you hit to turn the page.
        BlockPos dial = dial();
        level.setBlock(dial.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(dial, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(dial.above(), Blocks.LANTERN.defaultBlockState(), 2);
        sign(level, dial.south(), Direction.SOUTH,
                "§0THE CHART", "§0Use the stone", "§0to turn the page.", "");

        // The second stone: how far in it is drawn.
        BlockPos zd = zoomDial();
        level.setBlock(zd.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(zd, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(zd.above(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        sign(level, zd.south(), Direction.SOUTH,
                "§0THE LENS", "§0Whole map,", "§0quarter, close.", "");
        drawn = null;
    }

    // ------------------------------------------------------------------
    // Drawing it
    // ------------------------------------------------------------------

    /** The mosaic block for one pixel, as a small code so refreshes can diff. */
    private static final byte UNKNOWN = 0;
    private static final byte GLADE = 1;
    private static final byte WALKED = 2;
    private static final byte MARKED = 3;
    private static final byte RUNNER = 4;
    /** Standing stone, drawn only where a tile is small enough to mean one. */
    private static final byte WALL = 5;
    /** Open corridor, as against merely "a region somebody has been through". */
    private static final byte CORRIDOR = 6;
    private static final byte EXIT = 7;
    private static final byte HOLE = 8;
    private static final byte DEAD_GLADE = 9;
    private static final byte DOOR = 10;

    /**
     * Nine colours where there were four.
     *
     * <p>Concrete throughout, because it is the only block family with enough
     * flat, unmistakable colours to build a legend out of - and a legend is what
     * this is. Every one of these has to be identifiable from head height while
     * somebody else is arguing with you about it.
     */
    private static BlockState paint(byte code) {
        return switch (code) {
            case GLADE -> Blocks.GREEN_CONCRETE.defaultBlockState();
            case WALKED -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case MARKED -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case RUNNER -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case WALL -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case CORRIDOR -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case EXIT -> Blocks.GOLD_BLOCK.defaultBlockState();
            case HOLE -> Blocks.RED_CONCRETE.defaultBlockState();
            case DEAD_GLADE -> Blocks.BROWN_CONCRETE.defaultBlockState();
            case DOOR -> Blocks.LIME_CONCRETE.defaultBlockState();
            default -> Blocks.BLACK_CONCRETE.defaultBlockState();
        };
    }

    /** Cells across, at the current zoom. */
    private static int span() {
        return ZOOM_CELLS[Math.floorMod(zoom, ZOOM_CELLS.length)];
    }

    /** North-west cell of the window the floor is showing. */
    private static int windowX() {
        int sp = span();
        return Math.max(0, Math.min(MazeData.GRID - sp, focusX - sp / 2));
    }

    private static int windowZ() {
        int sp = span();
        return Math.max(0, Math.min(MazeData.GRID - sp, focusZ - sp / 2));
    }

    /**
     * Redraws whatever has changed.
     *
     * <p>Called once a second. Seventeen hundred blocks is far too many to write
     * every second, so it keeps what it drew and only touches pixels that moved -
     * which in practice is the handful under the Runners plus whatever they have
     * just revealed.
     */
    public static void refresh(ServerLevel level) {
        if (level.getServer() == null) {
            return;
        }
        MazeCharts charts = MazeCharts.get(level.getServer());
        List<String> known = charts.charts();
        String layout = known.isEmpty() ? null : known.get(Math.floorMod(showing, known.size()));

        MazeData.Layout live = MazeRuntime.todaysLayout(level);
        // Walls are only drawn for the chart that is actually standing tonight.
        MazeData.Layout graph = live != null && layout != null && live.name().equals(layout)
                ? live : null;

        byte[] want = new byte[SIZE * SIZE];
        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                want[pz * SIZE + px] = sample(level, charts, layout, graph, px, pz);
            }
        }
        // Live positions, drawn last so a Runner is never hidden under a colour.
        // Only for the chart standing today - a blue dot on last Tuesday's chart
        // would be a lie about where somebody is.
        if (graph != null) {
            int sp = span();
            int wx = windowX();
            int wz = windowZ();
            int scale = SIZE / sp;
            for (ServerPlayer p : level.players()) {
                int cellX = p.blockPosition().getX() / MazeData.CELL;
                int cellZ = p.blockPosition().getZ() / MazeData.CELL;
                if (MazeData.inGlade(cellX, cellZ)) {
                    continue;
                }
                if (cellX < wx || cellZ < wz || cellX >= wx + sp || cellZ >= wz + sp) {
                    continue; // outside the window the floor is showing
                }
                if (scale >= 3) {
                    // A whole cell of blue at close range, so a runner is a
                    // person standing in a corridor rather than one lost pixel.
                    int bx = (cellX - wx) * scale;
                    int bz = (cellZ - wz) * scale;
                    for (int dx = 1; dx < scale - 1; dx++) {
                        for (int dz = 1; dz < scale - 1; dz++) {
                            mark(want, bx + dx, bz + dz);
                        }
                    }
                } else {
                    mark(want, (cellX - wx) * SIZE / sp, (cellZ - wz) * SIZE / sp);
                }
            }
        }

        // A full repaint once a minute. The diff is what makes this cheap, but it
        // also means anything that changes a pixel behind the renderer's back -
        // somebody standing a torch on the map, a creeper, an operator - stays
        // wrong forever, because the renderer believes it already painted that
        // block. Self-healing beats being right about the cache.
        if (drawn == null || drawn.length != want.length
                || level.getGameTime() % FULL_REPAINT_TICKS == 0L) {
            drawn = new byte[want.length];
            java.util.Arrays.fill(drawn, (byte) -1);
        }
        int ox = originX();
        int oz = originZ();
        for (int i = 0; i < want.length; i++) {
            if (want[i] == drawn[i]) {
                continue;
            }
            drawn[i] = want[i];
            level.setBlock(new BlockPos(ox + i % SIZE, Y, oz + i / SIZE), paint(want[i]), 2);
        }
    }

    private static void mark(byte[] want, int px, int pz) {
        if (px >= 0 && pz >= 0 && px < SIZE && pz < SIZE) {
            want[pz * SIZE + px] = RUNNER;
        }
    }

    /**
     * What one pixel of the mosaic should be.
     *
     * <p>Forty-two blocks across ninety-six cells, so a pixel covers a little
     * over two cells and the sample is a union rather than a midpoint: if any
     * cell under this block has been walked, the block lights. Taking the middle
     * cell instead would make single-width corridors flicker in and out of
     * existence depending on which side of the boundary they fell, and a map that
     * drops lanes is worse than no map.
     */
    private static byte sample(ServerLevel level, MazeCharts charts, String layout,
                               MazeData.Layout live, int px, int pz) {
        int sp = span();
        int wx = windowX();
        int wz = windowZ();

        // Three tiles to the cell or better: draw the maze itself - the walls,
        // the openings between them, the shape of the junction. Below that a
        // tile covers more than one cell and there is no honest way to draw a
        // wall, so it stays a coverage map.
        int scale = SIZE / sp;
        if (scale >= 3) {
            int cx = wx + px / scale;
            int cz = wz + pz / scale;
            int sx = px % scale;
            int sz = pz % scale;
            return fine(level, charts, layout, live, cx, cz, sx, sz, scale);
        }

        int x0 = wx + px * sp / SIZE;
        int x1 = Math.max(x0 + 1, wx + (px + 1) * sp / SIZE);
        int z0 = wz + pz * sp / SIZE;
        int z1 = Math.max(z0 + 1, wz + (pz + 1) * sp / SIZE);

        boolean glade = false;
        boolean dead = false;
        boolean walked = false;
        boolean marked = false;
        boolean exit = false;
        boolean hole = false;
        boolean door = false;
        for (int cx = x0; cx < x1 && cx < MazeData.GRID; cx++) {
            for (int cz = z0; cz < z1 && cz < MazeData.GRID; cz++) {
                if (MazeData.inGlade(cx, cz)) {
                    glade = true;
                    continue;
                }
                boolean known = layout == null ? charts.charted(cx, cz)
                        : charts.chartedOn(layout, cx, cz);
                if (DeadGlade.coversCell(cx, cz)) {
                    // Only once somebody has actually been there. A ruin you have
                    // not found should not be sitting on the map waiting.
                    dead |= known;
                }
                if (charts.marked(cx, cz)) {
                    marked = true;
                }
                if (known) {
                    walked = true;
                    if (isDoorCell(cx, cz)) {
                        door = true;
                    }
                    if (live != null && isExitCell(live, cx, cz)) {
                        exit = true;
                    }
                    if (isHoleCell(cx, cz)) {
                        hole = true;
                    }
                }
            }
        }
        // Landmarks beat coverage: the whole reason to look at this thing is to
        // find the four or five places that matter.
        if (exit) {
            return EXIT;
        }
        if (hole) {
            return HOLE;
        }
        if (door) {
            return DOOR;
        }
        if (dead) {
            return DEAD_GLADE;
        }
        if (glade) {
            return GLADE;
        }
        if (marked && walked) {
            return MARKED;
        }
        return walked ? WALKED : UNKNOWN;
    }

    /**
     * One cell, drawn at three tiles a side or better.
     *
     * <p>The middle is the corridor and the four edges are the walls between
     * this cell and its neighbours, read from the live graph. This is the whole
     * point of the zoom: at this size "there is a way through to the north" is
     * something the floor can say, and at any coarser resolution it simply
     * cannot.
     *
     * <p>Corners are always wall. They are where four cells meet and there is
     * never an opening there.
     */
    private static byte fine(ServerLevel level, MazeCharts charts, String layout,
                             MazeData.Layout live, int cx, int cz, int sx, int sz, int scale) {
        if (cx >= MazeData.GRID || cz >= MazeData.GRID) {
            return UNKNOWN;
        }
        if (MazeData.inGlade(cx, cz)) {
            return GLADE;
        }
        boolean known = layout == null ? charts.charted(cx, cz)
                : charts.chartedOn(layout, cx, cz);
        if (!known) {
            return UNKNOWN;
        }

        int mid = scale / 2;
        boolean edgeN = sz == 0;
        boolean edgeS = sz == scale - 1;
        boolean edgeW = sx == 0;
        boolean edgeE = sx == scale - 1;
        boolean midX = sx == mid;
        boolean midZ = sz == mid;

        // The middle of the tile: the cell itself and whatever is standing in it.
        if (!edgeN && !edgeS && !edgeW && !edgeE) {
            if (DeadGlade.coversCell(cx, cz)) {
                return DEAD_GLADE;
            }
            if (live != null && isExitCell(live, cx, cz)) {
                return EXIT;
            }
            if (isHoleCell(cx, cz)) {
                return HOLE;
            }
            if (isDoorCell(cx, cz)) {
                return DOOR;
            }
            if (charts.marked(cx, cz)) {
                return MARKED;
            }
            return CORRIDOR;
        }
        // The edges: wall unless the graph says there is a way through, and only
        // for the chart that is actually standing - a wall on last Tuesday's map
        // is a guess.
        if (live == null) {
            return WALL;
        }
        if (midX && edgeN) {
            return MazeData.isOpen(cx, cz, cx, cz - 1, live) ? CORRIDOR : WALL;
        }
        if (midX && edgeS) {
            return MazeData.isOpen(cx, cz, cx, cz + 1, live) ? CORRIDOR : WALL;
        }
        if (midZ && edgeW) {
            return MazeData.isOpen(cx, cz, cx - 1, cz, live) ? CORRIDOR : WALL;
        }
        if (midZ && edgeE) {
            return MazeData.isOpen(cx, cz, cx + 1, cz, live) ? CORRIDOR : WALL;
        }
        return WALL;
    }

    private static boolean isDoorCell(int cx, int cz) {
        for (int[] d : MazeBuilder.DOOR_CELLS) {
            if (d[0] == cx && d[1] == cz) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExitCell(MazeData.Layout live, int cx, int cz) {
        MazeData.Exit ex = MazeData.exit(live.exit());
        return ex != null && ex.cellX() == cx && ex.cellZ() == cz;
    }

    private static boolean isHoleCell(int cx, int cz) {
        for (int i = 0; i < GrieverHoles.COUNT; i++) {
            BlockPos h = GrieverHoles.hole(i);
            if (h.getX() / MazeData.CELL == cx && h.getZ() / MazeData.CELL == cz) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Turning the page
    // ------------------------------------------------------------------

    public static void cycle(ServerLevel level, ServerPlayer who) {
        if (level.getServer() == null) {
            return;
        }
        MazeCharts charts = MazeCharts.get(level.getServer());
        List<String> known = charts.charts();
        if (known.isEmpty()) {
            who.displayClientMessage(Component.literal(
                    "§7Nothing charted yet. §8Somebody has to go out there."), false);
            return;
        }
        showing = Math.floorMod(showing + 1, known.size());
        drawn = null; // force a full repaint on the next refresh
        String layout = known.get(showing);
        who.displayClientMessage(Component.literal(
                "§6CHART " + MazeCharts.label(showing) + " §8— §f"
                        + charts.percentOn(layout) + "%§7 of it walked"
                        + (isToday(level, layout) ? " §a(this is tonight's)" : "")), false);
        level.playSound(null, dial(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /**
     * Turning the lens.
     *
     * <p>Whole map, quarter, close - and at close the floor centres on wherever
     * the last person to walk out there got to, because a close map of the Glade
     * is a close map of the one place nobody needs one.
     */
    public static void zoomCycle(ServerLevel level, ServerPlayer who) {
        zoom = Math.floorMod(zoom + 1, ZOOM_CELLS.length);
        drawn = null;
        int sp = span();
        int scale = SIZE / sp;
        who.displayClientMessage(Component.literal(
                "§bTHE LENS §8— §f" + ZOOM_NAME[zoom] + "§7, " + sp + " cells across"
                        + (scale >= 3 ? " §a(walls drawn)" : "")), false);
        if (scale >= 3) {
            who.displayClientMessage(Component.literal(
                    "§8centred on " + focusX + ", " + focusZ + " — where somebody last was"), false);
        }
        level.playSound(null, zoomDial(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
        refresh(level);
    }

    private static boolean isToday(ServerLevel level, String layout) {
        MazeData.Layout today = MazeRuntime.todaysLayout(level);
        return today != null && today.name().equals(layout);
    }

    /** Which chart is on the floor, for the status readouts. */
    public static String showingLabel(ServerLevel level) {
        if (level.getServer() == null) {
            return "-";
        }
        List<String> known = MazeCharts.get(level.getServer()).charts();
        return known.isEmpty() ? "-" : MazeCharts.label(Math.floorMod(showing, known.size()));
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
