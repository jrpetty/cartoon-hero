package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    /** Bumped on a wipe, so the next run gets a different way out. */
    private static int layoutShift = 0;
    private static boolean doorsOpen = false;
    private static long lastPhaseDay = -1;
    private static boolean warnedDusk = false;

    /** One status bar per runner: day, layout, doors, and their own clock. */
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    /** Who has already been read the rules. */
    private static final java.util.Set<UUID> BRIEFED = new java.util.HashSet<>();
    /** Who is currently caught outside the Glade for the night. */
    private static final java.util.Set<UUID> NIGHT_OUT = new java.util.HashSet<>();

    private MazeRuntime() {
    }

    public static void reset() {
        weekOrder = null;
        appliedDay = -1;
        doorsOpen = false;
        lastPhaseDay = -1;
        warnedDusk = false;
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
        }
        BARS.clear();
        MazeRuns.clearAll();
        MazeSting.clearAll();
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
        return MazeData.layout(weekOrder[(int) Math.floorMod(dayNumber(level) + layoutShift, n)]);
    }

    /**
     * Re-rolls the maze after a wipe, without rebuilding a single block.
     *
     * <p>Called when somebody walks back in and nobody else is inside - which is
     * what "everyone died" looks like from here. The world itself is left exactly
     * as it was found, because the build is never re-run: whatever state the maze
     * had reached carries over. Only the layout moves on, which re-seals the old
     * exit, opens a new one, and cuts fresh routes to it.
     *
     * <p>So the progress you made on the world survives your death and the way out
     * does not.
     */
    public static void rerollAfterWipe(ServerLevel level) {
        layoutShift++;
        appliedDay = -1;
        MazeData.Layout next = todaysLayout(level);
        if (next != null) {
            applyLayout(level, next);
            appliedDay = dayNumber(level);
        }
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

        // First run, or a fresh day: put today's walls in place and wipe every
        // per-day tally with them.
        if (appliedDay != day) {
            boolean rollover = appliedDay >= 0;
            applyLayout(level, todaysLayout(level));
            appliedDay = day;
            // Fresh caches with the fresh walls: yesterday's map of where things
            // were should be worth nothing.
            MazeChests.reshuffle(level, day);
            if (rollover) {
                newDay(level, day);
            }
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

        MazeRace.tick(level);
        MazeSting.tick(level);
        portalAmbience(level, todaysLayout(level));
        tickRunners(level, t);
        tickGrievers(level, t);
    }

    /**
     * Everything that is measured in days gets put back to zero here.
     *
     * <p>The maze reshapes at midnight and the day number goes up, but the state
     * hanging off it did not: a run clock started yesterday kept counting through
     * the rollover into a maze that no longer had the corridor it was timing, and
     * a sting tally survived a night's sleep in the Glade. Both make the day
     * counter decorative - it advertises a fresh start the game does not give you.
     *
     * <p>So a new day is a new day. Clocks stop, tallies clear, and the venom in
     * anyone who made it back is out of their system.
     */
    private static void newDay(ServerLevel level, long day) {
        MazeRuns.clearAll();
        MazeSting.clearAll();
        NIGHT_OUT.clear();
        for (ServerPlayer p : level.players()) {
            p.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
            p.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
            p.removeEffect(net.minecraft.world.effect.MobEffects.CONFUSION);
            p.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
            p.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            p.displayClientMessage(Component.literal(
                    "§6§lDAY " + (day + 1)), false);
            p.displayClientMessage(Component.literal(
                    "§7The walls moved in the night. §8Clocks and tallies are clear."), false);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.AMBIENT, 0.8F, 1.1F);
        }
    }

    /** Night is everything from the doors sealing to dawn. */
    public static boolean isNight(long timeOfDay) {
        return timeOfDay >= DOORS_SEAL;
    }

    /**
     * Per-runner upkeep: brief newcomers, start a clock the moment someone leaves
     * the Glade, finish it if they reach the live exit, and keep the status bar
     * honest.
     */
    private static void tickRunners(ServerLevel level, long t) {
        MazeData.Layout layout = todaysLayout(level);
        for (ServerPlayer p : level.players()) {
            brief(p);
            BlockPos at = p.blockPosition();
            int cellX = at.getX() / MazeData.CELL;
            int cellZ = at.getZ() / MazeData.CELL;
            boolean inGlade = MazeData.inGlade(cellX, cellZ);

            if (!inGlade && !MazeRuns.isRunning(p.getUUID())) {
                MazeRuns.begin(level, p);
                MazeAdvancements.grant(p, MazeAdvancements.ROOT);
                MazeAdvancements.grant(p, MazeAdvancements.FIRST_RUN);
            }
            // Still out there when the doors shut, and still out there at dawn.
            if (!inGlade && isNight(t)) {
                NIGHT_OUT.add(p.getUUID());
            } else if (!isNight(t) && NIGHT_OUT.remove(p.getUUID())) {
                MazeAdvancements.grant(p, MazeAdvancements.SURVIVE_NIGHT);
            }
            if (layout != null && MazeRuns.isRunning(p.getUUID()) && atExit(at, layout)) {
                int seconds = MazeRuns.complete(level, p, layout);
                MazeAdvancements.grant(p, MazeAdvancements.FIRST_ESCAPE);
                if (isNight(t)) {
                    // Out through a maze that has already sealed behind you.
                    MazeAdvancements.grant(p, MazeAdvancements.CLEAN_ESCAPE);
                }
                if (seconds >= 0 && MazeRace.onEscape(level, p, seconds)) {
                    MazeAdvancements.grant(p, MazeAdvancements.RACE_WIN);
                }
                sendHome(level, p, seconds);
            }
            updateBar(level, p, layout, t);
        }
        // Drop bars for anyone who has left the dimension. The bar has to be
        // emptied, not just forgotten: dropping the reference alone leaves the
        // maze's status bar stuck on their screen out in the real world.
        BARS.entrySet().removeIf(e -> {
            if (level.getPlayerByUUID(e.getKey()) != null) {
                return false;
            }
            e.getValue().removeAllPlayers();
            return true;
        });
    }

    /**
     * Makes the live exit look and sound like a way out rather than a gap.
     *
     * <p>The doorway is deliberately still air - a real portal block would either
     * do vanilla's job and send people somewhere else entirely, or need a block,
     * a model and a registration to do a job that particles already do. What was
     * missing was any sign that the thing in front of you was the thing you had
     * been looking for. A doorway that hums and throws light is unmistakable at
     * the far end of a corridor, and it costs one particle call a second.
     */
    private static void portalAmbience(ServerLevel level, MazeData.Layout layout) {
        if (layout == null || level.players().isEmpty()) {
            return;
        }
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null) {
            return;
        }
        int[] p = MazeData.exitPortal(ex);
        boolean alongX = "north".equals(ex.facing()) || "south".equals(ex.facing());
        boolean anyoneNear = false;
        for (ServerPlayer pl : level.players()) {
            if (pl.blockPosition().distSqr(new BlockPos(p[0], p[1], p[2])) < 64.0 * 64.0) {
                anyoneNear = true;
                break;
            }
        }
        if (!anyoneNear) {
            return;
        }
        for (int w = 0; w < 2; w++) {
            double x = p[0] + (alongX ? w : 0) + 0.5;
            double z = p[2] + (alongX ? 0 : w) + 0.5;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    x, MazeData.WALL_BASE_Y + 1.5, z, 24, 0.35, 1.4, 0.35, 0.35);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x, MazeData.WALL_BASE_Y + 2.0, z, 3, 0.25, 1.0, 0.25, 0.005);
        }
        level.playSound(null, new BlockPos(p[0], p[1], p[2]),
                SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.1F);
    }

    /** Within a couple of blocks of today's exit portal. */
    private static boolean atExit(BlockPos at, MazeData.Layout layout) {
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null) {
            return false;
        }
        int[] p = MazeData.exitPortal(ex);
        return Math.abs(at.getX() - p[0]) <= 2 && Math.abs(at.getZ() - p[2]) <= 2
                && Math.abs(at.getY() - p[1]) <= 3;
    }

    /**
     * Dresses the live exit as a portal you can pick out at a distance.
     *
     * <p>It was a gap in a wall with a lantern over it, which at the end of a long
     * corridor looks like every other gap in every other wall. A framed, lit
     * doorway is the one thing in the maze that says <em>this is the way out</em>
     * without a marker on your screen telling you so.
     */
    private static void portalFrame(ServerLevel level, MazeData.Exit ex, int[] p) {
        boolean alongX = "north".equals(ex.facing()) || "south".equals(ex.facing());
        for (int side = -1; side <= 2; side++) {
            for (int dy = -1; dy <= 5; dy++) {
                boolean jamb = side == -1 || side == 2;
                boolean head = dy == -1 || dy == 5;
                if (!jamb && !head) {
                    continue;
                }
                BlockPos at = new BlockPos(
                        p[0] + (alongX ? side : 0),
                        MazeData.WALL_BASE_Y + dy,
                        p[2] + (alongX ? 0 : side));
                level.setBlock(at, jamb && head
                        ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                        : jamb ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        // A pair of lights on the jambs, so it reads as lit rather than as a hole.
        for (int side : new int[]{-1, 2}) {
            level.setBlock(new BlockPos(
                    p[0] + (alongX ? side : 0),
                    MazeData.WALL_BASE_Y + 2,
                    p[2] + (alongX ? 0 : side)), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
    }

    /**
     * Escaping takes you out of the maze entirely, and pays.
     *
     * <p>It used to drop you back in the Glade, which made getting out the same
     * as not getting out: the way through was solved and the reward for solving
     * it was standing where you began. Going through the portal now returns you
     * to the teleporter you came in by, with your time recorded and a bonus
     * scaled to how fast you ran it.
     */
    private static void sendHome(ServerLevel level, ServerPlayer p, int seconds) {
        escapeBonus(p, seconds);
        MazeEvents.returnToTeleporter(p);
    }

    /**
     * What getting out is worth. Faster runs pay more, and everything in the
     * table is a material - nothing here shortcuts an arena.
     */
    private static void escapeBonus(ServerPlayer p, int seconds) {
        // Beating the maze is the hardest single thing in the mod: a route you had
        // to find, at night, with Grievers in it, having survived four stings or
        // avoided them. The old payout was a handful of iron - less than an hour in
        // a cave - which said the run had not been worth doing. It pays properly
        // now, and it pays for speed, because the fast route is the one you had to
        // learn the maze to find.
        int tier = seconds < 0 ? 1 : seconds <= 180 ? 3 : seconds <= 420 ? 2 : 1;
        java.util.List<net.minecraft.world.item.ItemStack> paid = new java.util.ArrayList<>();
        paid.add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.DIAMOND, 12 * tier));
        paid.add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.NETHERITE_SCRAP, tier));
        paid.add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.GOLD_INGOT, 24 * tier));
        paid.add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.IRON_INGOT, 32 * tier));
        paid.add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.LAPIS_LAZULI, 24 * tier));
        paid.add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, tier));
        if (tier >= 2) {
            paid.add(new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.NETHERITE_INGOT, tier - 1));
        }
        if (tier >= 3) {
            // Only the fast route gets this, and it should be visibly the prize.
            paid.add(new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.NETHERITE_BLOCK));
            paid.add(new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.ENCHANTED_BOOK));
        }
        for (net.minecraft.world.item.ItemStack stack : paid) {
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        }
        p.giveExperiencePoints(1500 * tier);
        p.displayClientMessage(Component.literal(
                "§6§lOUT. §r§7Tier §f" + tier + "§7 payout"
                        + (tier == 3 ? " §8— you ran it clean." : "")), false);
    }

    private static void brief(ServerPlayer p) {
        if (!AbyssConfig.MAZE_SHOW_BRIEFING.get() || !BRIEFED.add(p.getUUID())) {
            return;
        }
        p.displayClientMessage(Component.literal("§2§l── THE GLADE ──"), false);
        p.displayClientMessage(Component.literal(
                "§7The doors open at dawn and seal at dusk. Find the way out and get back before they shut."), false);
        p.displayClientMessage(Component.literal(
                "§7The walls move every night, and the way out moves with them. §cGrievers hunt after dark."), false);
        p.displayClientMessage(Component.literal(
                "§c§lDie and the walls put you out. §8There is no second try at a run."), false);
        p.displayClientMessage(Component.literal(
                "§7A Griever sting is survivable — §cfour§7 is not. Serum clears the tally."), false);
        p.displayClientMessage(Component.literal(
                "§8/maze status · /maze leaderboard"), false);
    }

    /** The status bar: day, layout, doors, and your own clock if one is running. */
    private static void updateBar(ServerLevel level, ServerPlayer p, MazeData.Layout layout, long t) {
        ServerBossEvent bar = BARS.get(p.getUUID());
        if (bar == null) {
            bar = new ServerBossEvent(Component.literal("The Maze"),
                    BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(p);
            BARS.put(p.getUUID(), bar);
        }
        int run = MazeRuns.elapsedSeconds(level, p.getUUID());
        String title = "§fDay §e" + (dayNumber(level) + 1)
                + " §8| §f" + (layout == null ? "?" : layout.name())
                + " §8| " + (doorsOpen ? "§aDOORS OPEN" : "§4DOORS SEALED")
                + (run >= 0 ? " §8| §b" + MazeRuns.format(run) : "")
                + MazeSting.hudFragment(p.getUUID());
        bar.setName(Component.literal(title));
        bar.setColor(isNight(t) ? BossEvent.BossBarColor.RED
                : doorsOpen ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW);
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, (float) t / (float) DAY_TICKS)));
    }

    /**
     * Grievers, after dark only. Kept to a cap per runner and topped up slowly
     * rather than all at once, so the night fills up rather than opens with a
     * wall of them.
     */
    private static void tickGrievers(ServerLevel level, long t) {
        List<Mob> loaded = Griever.loaded(level);
        if (!isNight(t) || !AbyssConfig.GRIEVERS_ENABLED.get()) {
            // Dawn: whatever is left walks back to the corners it came out of,
            // and is cleared once it is there and out of everybody's way.
            if (!loaded.isEmpty()) {
                if (!isNight(t)) {
                    Griever.recall(level, loaded);
                }
                for (Mob g : loaded) {
                    g.discard();
                }
            }
            return;
        }
        // Before anything else: the clearing is not theirs, ever.
        Griever.keepOut(level, loaded);
        RandomSource rng = RandomSource.create();
        List<ServerPlayer> runners = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            int cellX = p.blockPosition().getX() / MazeData.CELL;
            int cellZ = p.blockPosition().getZ() / MazeData.CELL;
            if (!MazeData.inGlade(cellX, cellZ)) {
                runners.add(p); // the Glade is never hunted
            }
        }
        if (runners.isEmpty()) {
            return;
        }
        for (Mob g : loaded) {
            if (g.getTarget() instanceof ServerPlayer tp
                    && tp.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                g.setTarget(null);
            }
        }
        int cap = Griever.capFor(level, runners.size());
        if (loaded.size() < cap && rng.nextInt(3) == 0) {
            Griever.spawnNear(level, runners.get(rng.nextInt(runners.size())), rng);
        }
        Griever.ambience(level, loaded, rng);
        Griever.pressure(level, loaded);
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
        guaranteeRoutes(level, layout);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§5The walls grind. §7The maze has changed."), false);
        }
    }

    /**
     * Guarantees every door can reach today's exit, by separate routes.
     *
     * <p>Nothing else in the maze checks that the exit is reachable. The dataset's
     * connectivity plus a night's toggles could perfectly well leave a runner in a
     * maze with no solution at all, and there would be no symptom other than
     * somebody wandering until the clock ran out.
     *
     * <p>So each of the four doors gets its own carved route to the live exit,
     * each with a different hash salt. That does two things at once: it makes the
     * promise that a way through exists from wherever you started, and it stops
     * the four doors being the same corridor - they wander apart before they
     * converge on the way out.
     */
    private static void guaranteeRoutes(ServerLevel level, MazeData.Layout layout) {
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null) {
            return;
        }
        int salt = 1;
        for (int[] door : MazeBuilder.DOOR_CELLS) {
            MazeBuilder.carveRoute(level, door[0], door[1], ex.cellX(), ex.cellZ(),
                    layout.name().hashCode() + salt * 7919);
            salt++;
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
                // The way out should be visible from a long way down a corridor.
                level.setBlock(new BlockPos(p[0], MazeData.FLOOR_Y, p[2]),
                        Blocks.SEA_LANTERN.defaultBlockState(), 2);
                for (int dy = 1; dy <= 4; dy++) {
                    level.setBlock(new BlockPos(p[0], MazeData.WALL_BASE_Y + dy, p[2] + 1),
                            Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(p[0], MazeData.WALL_BASE_Y + 5, p[2]),
                        Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(p[0], MazeData.WALL_BASE_Y + 4, p[2]),
                        Blocks.LANTERN.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true), 2);
                portalFrame(level, ex, p);
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
                        // Closed, a door is a single great slab of polished stone
                        // banded in chiselled courses - unmistakably a door, and
                        // not to be confused with the wall either side of it.
                        boolean band = ((y - MazeData.WALL_BASE_Y) % 4) == 0;
                        level.setBlock(new BlockPos(x, y, z), open
                                ? Blocks.AIR.defaultBlockState()
                                : (band ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                                : Blocks.POLISHED_DEEPSLATE.defaultBlockState()), 2);
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

    /** Tears down everything the maze was showing a player who has left it. */
    public static void onPlayerLeft(java.util.UUID id) {
        ServerBossEvent bar = BARS.remove(id);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        NIGHT_OUT.remove(id);
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
