package com.jrpetty.aztecabyss.maze;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The live maze: the daily clock, the doors, and the nightly reshape.
 *
 * <p>A day is ninety real minutes - sixty of daylight, thirty of night - and the
 * clock is read straight off the world's day time, so the maze's schedule and
 * Minecraft's are the same schedule:
 *
 * <ul>
 *   <li>{@code 1000} the Glade doors grind open</li>
 *   <li>{@code 11500} the dusk warning sounds</li>
 *   <li>{@code 12500} the doors seal - anyone still inside is inside for the night</li>
 *   <li>{@code 18000} the maze reshapes</li>
 *   <li>{@code 24000} dawn, and the next layout takes over</li>
 * </ul>
 *
 * <p>The seven layouts are shuffled once per world from its seed and then repeat
 * weekly, so a given server's week is stable and learnable - which is the entire
 * point of a maze you are meant to chart.
 */
public final class MazeRuntime {

    public static final long DOORS_OPEN = 1000L;
    public static final long DUSK_WARNING = 11500L;
    public static final long DOORS_SEAL = 12500L;
    public static final long RESHAPE = 18000L;
    public static final long DAY_TICKS = 24000L;

    /** The four Glade doors, as ring cells read from the dataset's meta. */
    private static final int[][] DOOR_CELLS = {
            {48, 39}, // north
            {56, 48}, // east
            {47, 56}, // south
            {39, 47}, // west
    };
    private static final String[] DOOR_NAMES = {"NORTH", "EAST", "SOUTH", "WEST"};

    /** Layout order for this world, shuffled once from the seed. */
    private static int[] weekOrder = null;
    /** The day whose layout is currently standing in the world, or -1. */
    private static long appliedDay = -1;
    private static boolean doorsOpen = false;
    private static long lastPhaseDay = -1;
    private static boolean warnedDusk = false;

    private MazeRuntime() {
    }

    public static void reset() {
        weekOrder = null;
        appliedDay = -1;
        doorsOpen = false;
        lastPhaseDay = -1;
        warnedDusk = false;
    }

    /** Which day of the run this is, counting from world start. */
    public static long dayNumber(ServerLevel level) {
        return level.getDayTime() / DAY_TICKS;
    }

    public static long timeOfDay(ServerLevel level) {
        return level.getDayTime() % DAY_TICKS;
    }

    public static boolean doorsOpen() {
        return doorsOpen;
    }

    /** The layout standing today. */
    public static MazeData.Layout todaysLayout(ServerLevel level) {
        ensureOrder(level);
        int n = MazeData.layouts().size();
        if (n == 0) {
            return null;
        }
        return MazeData.layout(weekOrder[(int) Math.floorMod(dayNumber(level), n)]);
    }

    /**
     * Shuffles the week once, deterministically from the world seed. Same world,
     * same week, every time - so a route learned on day three is still worth
     * something next Wednesday.
     */
    private static void ensureOrder(ServerLevel level) {
        if (weekOrder != null) {
            return;
        }
        int n = Math.max(1, MazeData.layouts().size());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new java.util.Random(level.getSeed()));
        weekOrder = new int[n];
        for (int i = 0; i < n; i++) {
            weekOrder[i] = order.get(i);
        }
    }

    /**
     * Drives the whole live loop. Called once a second rather than every tick -
     * nothing here is finer-grained than a clock event, and the reshape is the
     * only expensive thing it ever does.
     */
    public static void tick(ServerLevel level) {
        if (MazeBuilder.isBuilding()) {
            MazeBuilder.tick(level);
            return;
        }
        MazeData.load();
        long day = dayNumber(level);
        long t = timeOfDay(level);

        // First run, or a fresh day: put today's walls in place.
        if (appliedDay != day) {
            applyLayout(level, todaysLayout(level));
            appliedDay = day;
        }
        if (lastPhaseDay != day) {
            lastPhaseDay = day;
            warnedDusk = false;
        }

        boolean shouldBeOpen = t >= DOORS_OPEN && t < DOORS_SEAL;
        if (shouldBeOpen != doorsOpen) {
            setDoors(level, shouldBeOpen);
        }
        if (!warnedDusk && t >= DUSK_WARNING && t < DOORS_SEAL) {
            warnedDusk = true;
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(Component.literal(
                        "§c⚠ The doors begin to close. Get back to the Glade.").withStyle(ChatFormatting.BOLD), false);
                level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 0.5F);
            }
        }
    }

    /**
     * Moves the two hundred toggles to match a layout.
     *
     * <p>Only the toggles ever move - the base graph is stamped once and never
     * touched again. That is what keeps a reshape to roughly fourteen thousand
     * blocks instead of two million, and it is also why every layout is
     * guaranteed solvable: the dataset only ever lists toggle sets that leave a
     * route to that day's exit.
     */
    public static void applyLayout(ServerLevel level, MazeData.Layout layout) {
        if (layout == null) {
            return;
        }
        RandomSource rng = RandomSource.create();
        for (MazeData.TogglePoint tp : MazeData.togglePoints().values()) {
            MazeBuilder.setToggle(level, tp, layout.open().contains(tp.id()), rng);
        }
        openExit(level, layout);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§5The walls grind. §7The maze has changed."), false);
        }
    }

    /** Cuts today's exit through the outer rim, and seals the other six. */
    private static void openExit(ServerLevel level, MazeData.Layout layout) {
        for (String id : new String[]{"exit_0", "exit_1", "exit_2", "exit_3", "exit_4", "exit_5", "exit_6"}) {
            MazeData.Exit ex = MazeData.exit(id);
            if (ex == null) {
                continue;
            }
            boolean live = id.equals(layout.exit());
            int[] p = MazeData.exitPortal(ex);
            for (int w = 0; w < 2; w++) {
                for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_BASE_Y + 3; y++) {
                    boolean alongX = "north".equals(ex.facing()) || "south".equals(ex.facing());
                    BlockPos at = new BlockPos(p[0] + (alongX ? w : 0), y, p[2] + (alongX ? 0 : w));
                    level.setBlock(at, live ? Blocks.AIR.defaultBlockState()
                            : Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            if (live) {
                level.setBlock(new BlockPos(p[0], MazeData.FLOOR_Y, p[2]),
                        Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        }
    }

    /** Opens or seals the four Glade doors. */
    private static void setDoors(ServerLevel level, boolean open) {
        doorsOpen = open;
        RandomSource rng = RandomSource.create();
        for (int i = 0; i < DOOR_CELLS.length; i++) {
            int cx = DOOR_CELLS[i][0];
            int cz = DOOR_CELLS[i][1];
            for (int lx = MazeData.CORRIDOR_MIN; lx <= MazeData.CORRIDOR_MAX; lx++) {
                for (int lz = 0; lz < MazeData.CELL; lz++) {
                    int x = cx * MazeData.CELL + lx;
                    int z = cz * MazeData.CELL + lz;
                    for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_TOP_Y; y++) {
                        level.setBlock(new BlockPos(x, y, z), open
                                ? Blocks.AIR.defaultBlockState()
                                : (rng.nextInt(4) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                                : Blocks.STONE_BRICKS.defaultBlockState()), 2);
                    }
                }
            }
        }
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(open
                    ? "§a▲ The doors are open. §7Run."
                    : "§4▼ The doors have sealed."), false);
            level.playSound(null, p.blockPosition(), SoundEvents.PISTON_EXTEND,
                    SoundSource.BLOCKS, 1.2F, 0.4F);
        }
    }

    /** A one-line status readable from a command or a HUD. */
    public static String status(ServerLevel level) {
        MazeData.Layout l = todaysLayout(level);
        long t = timeOfDay(level);
        return "Day " + (dayNumber(level) + 1)
                + " | layout " + (l == null ? "?" : l.name())
                + " | exit " + (l == null ? "?" : l.exit())
                + " | " + (doorsOpen ? "doors OPEN" : "doors SEALED")
                + " | t=" + t
                + (MazeBuilder.isBuilding() ? " | building " + MazeBuilder.progressPercent() + "%" : "");
    }

    /** Which of the eight compass sections a position falls in. */
    public static String section(BlockPos pos) {
        int mid = MazeData.SPAN / 2;
        int dx = pos.getX() - mid;
        int dz = pos.getZ() - mid;
        String ns = Math.abs(dz) < MazeData.SPAN / 8 ? "" : (dz < 0 ? "NORTH" : "SOUTH");
        String ew = Math.abs(dx) < MazeData.SPAN / 8 ? "" : (dx < 0 ? "WEST" : "EAST");
        String s = ns + ew;
        return s.isEmpty() ? "GLADE" : s;
    }
}
