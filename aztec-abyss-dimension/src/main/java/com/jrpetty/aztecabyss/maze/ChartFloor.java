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

    /** The floor sits one below standing height, so it reads as a sunken table. */
    private static final int Y = MazeData.FLOOR_Y;

    /** Which chart the floor is currently showing, as an index into discovery order. */
    private static int showing = 0;
    /** What is currently drawn, so a refresh only writes what changed. */
    private static byte[] drawn = null;

    private ChartFloor() {
    }

    public static void reset() {
        drawn = null;
        showing = 0;
    }

    /** The block you punch to change chart. */
    public static BlockPos dial() {
        return new BlockPos(originX() + SIZE / 2, Y + 1, originZ() + SIZE + 1);
    }

    // ------------------------------------------------------------------
    // Building it
    // ------------------------------------------------------------------

    /**
     * Lays the plaza: a rim, a sunken bed, and the dial on its south edge.
     *
     * <p>Sunk by one so that standing on the rim you are looking down at it. A
     * map at eye level is a wall; a map at your feet is a table, and a table is
     * the thing five people can crowd round.
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

    private static BlockState paint(byte code) {
        return switch (code) {
            case GLADE -> Blocks.GREEN_CONCRETE.defaultBlockState();
            case WALKED -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case MARKED -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case RUNNER -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            default -> Blocks.BLACK_CONCRETE.defaultBlockState();
        };
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

        byte[] want = new byte[SIZE * SIZE];
        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                want[pz * SIZE + px] = sample(charts, layout, px, pz);
            }
        }
        // Live positions, drawn last so a Runner is never hidden under a colour.
        // Only for the chart standing today - a blue dot on last Tuesday's chart
        // would be a lie about where somebody is.
        MazeData.Layout today = MazeRuntime.todaysLayout(level);
        if (today != null && layout != null && today.name().equals(layout)) {
            for (ServerPlayer p : level.players()) {
                int cellX = p.blockPosition().getX() / MazeData.CELL;
                int cellZ = p.blockPosition().getZ() / MazeData.CELL;
                if (MazeData.inGlade(cellX, cellZ)) {
                    continue;
                }
                int px = cellX * SIZE / MazeData.GRID;
                int pz = cellZ * SIZE / MazeData.GRID;
                if (px >= 0 && pz >= 0 && px < SIZE && pz < SIZE) {
                    want[pz * SIZE + px] = RUNNER;
                }
            }
        }

        if (drawn == null || drawn.length != want.length) {
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
    private static byte sample(MazeCharts charts, String layout, int px, int pz) {
        int x0 = px * MazeData.GRID / SIZE;
        int x1 = Math.max(x0 + 1, (px + 1) * MazeData.GRID / SIZE);
        int z0 = pz * MazeData.GRID / SIZE;
        int z1 = Math.max(z0 + 1, (pz + 1) * MazeData.GRID / SIZE);

        boolean glade = false;
        boolean walked = false;
        boolean marked = false;
        for (int cx = x0; cx < x1; cx++) {
            for (int cz = z0; cz < z1; cz++) {
                if (MazeData.inGlade(cx, cz)) {
                    glade = true;
                    continue;
                }
                if (charts.marked(cx, cz)) {
                    marked = true;
                }
                // On the selected chart if there is one, otherwise on everything
                // the Glade has ever brought back.
                if (layout == null ? charts.charted(cx, cz) : charts.chartedOn(layout, cx, cz)) {
                    walked = true;
                }
            }
        }
        if (marked && walked) {
            return MARKED;
        }
        if (walked) {
            return WALKED;
        }
        // The Glade is the one thing you know before you have been anywhere.
        return glade ? GLADE : UNKNOWN;
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
