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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live maze: the daily clock, the doors, and the nightly reshape.
 *
 * <p>A day is a day of daylight and a night of dark, both set in real seconds by
 * the config and ten minutes each out of the box:
 *
 * <ul>
 *   <li><b>dawn</b> the Glade doors grind open</li>
 *   <li><b>one minute to dusk</b> the warning sounds</li>
 *   <li><b>dusk</b> the doors seal - anyone still inside is inside for the night</li>
 *   <li><b>midnight</b> the maze reshapes, and is heard doing it</li>
 *   <li><b>dawn</b> the next layout is already standing</li>
 * </ul>
 *
 * <p>The seven layouts run in a fixed order. Which of them a game opens on is
 * drawn fresh every time the maze empties out, so one game starts on Saturday's
 * maze and goes Sunday, Monday, Tuesday, and the next might open on Wednesday's.
 * The order is the constant and the entry point is the variable - which is the
 * right way round for a maze you are meant to learn, because what you learned is
 * still true, you just came in at a different place in it.
 *
 * <p>The clock itself lives in {@link MazeClock}: the maze keeps its own time
 * rather than borrowing the overworld's, so a night is however long the config
 * says a night is and nobody can sleep through one from somewhere else.
 */
public final class MazeRuntime {

    // The schedule is no longer a set of magic tick numbers read off the
    // overworld's clock. Dawn is phase zero, dusk is the end of the day phase,
    // midnight is halfway through the night, and all three are derived from two
    // numbers in the config. See MazeClock.

    /** The four Glade doors, as ring cells read from the dataset's meta. */
    private static final int[][] DOOR_CELLS = {
            {48, 39}, // north
            {56, 48}, // east
            {47, 56}, // south
            {39, 47}, // west
    };
    private static final String[] DOOR_NAMES = {"NORTH", "EAST", "SOUTH", "WEST"};

    /**
     * The name of the layout whose walls are actually standing in the world.
     *
     * <p>Keyed on the name rather than the day number on purpose: a restart, a
     * midnight reshape and a brand new game then all reduce to the same question
     * - is what is standing what should be standing - instead of three separate
     * pieces of bookkeeping that can each go stale on their own.
     */
    private static String appliedLayoutName = null;
    /** The day the annex horde was last raised, so it happens once a day. */
    private static int hordeDay = -1;
    /** Marks the annex horde, so last night's can be swept when the exit moves. */
    private static final String LAST_STAND_TAG = "aztecabyss_laststand";
    private static boolean doorsOpen = false;
    private static boolean warnedDusk = false;

    /**
     * When the way out closes, as a game-time tick. Zero means nobody has found
     * it yet.
     */
    private static long escapeUntil = 0L;
    /** Who got through, in the order they did, for the summary. */
    private static final List<String> escapees = new ArrayList<>();

    /** How long the midnight reshape goes on making noise, in ticks. */
    private static final int RESHAPE_DRAMA_TICKS = 160;
    private static int reshapeDrama = 0;

    /** One status bar per runner: day, layout, doors, and their own clock. */
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    /** Who has already been read the rules. */
    private static final java.util.Set<UUID> BRIEFED = new java.util.HashSet<>();
    /** Who is currently caught outside the Glade for the night. */
    private static final java.util.Set<UUID> NIGHT_OUT = new java.util.HashSet<>();
    /** Where each runner was a second ago, for paying them by the metre. */
    private static final Map<UUID, BlockPos> lastSeen = new HashMap<>();
    /** Metres banked toward the next point. */
    private static final Map<UUID, Double> walked = new HashMap<>();
    /** The day each Runner last caught their second wind. */
    private static final Map<UUID, Integer> secondWind = new HashMap<>();

    private MazeRuntime() {
    }

    public static void reset() {
        appliedLayoutName = null;
        doorsOpen = false;
        warnedDusk = false;
        reshapeDrama = 0;
        hordeDay = -1;
        escapeUntil = 0L;
        escapees.clear();
        MazeJobs.clearCarried();
        lastSeen.clear();
        walked.clear();
        secondWind.clear();
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
        }
        BARS.clear();
        MazeRuns.clearAll();
        MazeSting.clearAll();
    }

    /** Which day of this game it is, from zero. */
    public static long dayNumber(ServerLevel level) {
        return MazeClock.get(level).day();
    }

    /** Ticks into the current maze day. */
    public static long timeOfDay(ServerLevel level) {
        return MazeClock.get(level).phase();
    }

    public static boolean doorsOpen() {
        return doorsOpen;
    }

    /**
     * The layout that should be standing right now.
     *
     * <p>Note the midnight step. After midnight the walls are already tomorrow's,
     * because that is when they move - so "today's layout" is a different answer
     * either side of it, and anyone caught out in the maze overnight is walking
     * tomorrow's corridors before tomorrow arrives. That is the whole horror of
     * the reshape and it only works if this method tells the truth about it.
     */
    public static MazeData.Layout todaysLayout(ServerLevel level) {
        MazeClock c = MazeClock.get(level);
        int mid = MazeClock.dayTicks() + MazeClock.nightTicks() / 2;
        return MazeDoors.get(level).ensure(c.session(),
                c.phase() >= mid ? c.day() + 1 : c.day());
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
        MazeClock clock = MazeClock.get(level);
        if (clock.played() && level.players().isEmpty()) {
            endGame(level, clock);
        }
    }

    /**
     * Watches for the end of a game.
     *
     * <p>A game runs from the first person walking in to the last one leaving,
     * however they leave. When the maze empties, that is the end of it - there is
     * no other definition available, and no other one is wanted: everybody got
     * out or everybody died are the only two ways this ends, and from in here
     * they look identical.
     *
     * @return true if a game just ended, in which case the caller should let the
     *         next tick start the new one from a clean slate
     */
    private static boolean sessionWatch(ServerLevel level, MazeClock clock) {
        if (!level.players().isEmpty()) {
            if (clock.markPlayed()) {
                // Day one's delivery. TheBox.deliver only ever ran from newDay,
                // and newDay only runs on a rollover - so day zero, the one day
                // the crate is meant to contain two cures, never got one at all.
                TheBox.deliver(level, clock.day());
                MazeNight.lift(level);
            }
            return false;
        }
        if (!clock.played()) {
            return false;
        }
        endGame(level, clock);
        return true;
    }

    /** Packs the game away and draws the next one's opening layout. */
    private static void endGame(ServerLevel level, MazeClock clock) {
        for (Mob g : Griever.loaded(level)) {
            g.discard();
        }
        clearLastStand(level);
        MazeRuns.clearAll();
        MazeSting.clearAll();
        NIGHT_OUT.clear();
        warnedDusk = false;
        // Null so the next tick stamps the new opening layout without needing to
        // know whether it differs from the old one.
        appliedLayoutName = null;
        hordeDay = -1;
        escapeUntil = 0L;
        escapees.clear();
        MazeDayWork.get(level).clearAll();
        MazeOrders.get(level).setHeads(0);
        MazeBell.reset();
        MazeRaid.reset(level);
        clock.newGame(level.getServer());
        MazeNotes.clearAll();
        GladeBuilder.forgetRoster();
    }

    /**
     * Drives the whole live loop. Called every tick now rather than once a
     * second, because the maze owns its clock and a clock that only looks at
     * itself once a second is a clock that is wrong for up to a second - which
     * is the difference between the doors sealing on you and not. The expensive
     * work below is still gated to once a second; only the clock, the sky and
     * the phase boundaries run at full rate.
     */
    public static void tick(ServerLevel level) {
        if (MazeBuilder.isBuilding()) {
            MazeBuilder.tick(level);
            return;
        }
        MazeData.load();
        MazeClock clock = MazeClock.get(level);

        boolean dawn = clock.advance(1);
        boolean midnight = clock.crossedMidnight(1);
        clock.pushSky(level);

        if (sessionWatch(level, clock)) {
            return;
        }

        // Is what is standing what should be standing? One question covers the
        // midnight reshape, a server restart and the first day of a new game.
        MazeData.Layout want = todaysLayout(level);
        String wantName = want == null ? null : want.name();
        if (!java.util.Objects.equals(appliedLayoutName, wantName)) {
            boolean first = appliedLayoutName == null;
            applyLayout(level, want);
            appliedLayoutName = wantName;
            // Fresh caches with the fresh walls: yesterday's map of where things
            // were should be worth nothing.
            MazeChests.reshuffle(level, clock.day());
            if (!first && midnight) {
                reshapeHeard(level);
            }
        }
        if (dawn) {
            warnedDusk = false;
            newDay(level, clock.day());
        }

        tickReshapeDrama(level);

        boolean shouldBeOpen = !clock.isNight();
        if (shouldBeOpen != doorsOpen) {
            setDoors(level, shouldBeOpen);
            if (!shouldBeOpen) {
                // The doors have just sealed. Whatever else lives here comes out
                // now, which is what makes the night a place rather than a wait.
                MazeNight.fall(level, clock.day());
            }
        }
        if (!warnedDusk && clock.inDuskWarning()) {
            warnedDusk = true;
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(Component.literal(
                        "§c⚠ The doors begin to close. Get back to the Glade.").withStyle(ChatFormatting.BOLD), false);
                // Dusk is the order deadline. Nothing comes up for anybody who
                // has not filed one, so the game has to say so while there is
                // still time to do something about it.
                if (clock.day() + 1 >= MazeOrders.REQUISITION_FROM_DAY) {
                    MazeOrders orders = MazeOrders.get(level);
                    if (orders.committedTotal() <= 0) {
                        // The pot is shared, so this is everybody's problem and
                        // is said to everybody rather than only to whoever has
                        // not personally clicked anything.
                        p.displayClientMessage(Component.literal(
                                "§e✦ The Glade has filed nothing. §7Nothing comes up at dawn. §8/maze order"), false);
                    } else if (orders.slate(p.getUUID()).isEmpty()) {
                        p.displayClientMessage(Component.literal(
                                "§7Others have ordered; you have not. §8"
                                        + MazeOrders.remaining(level) + " left in the pot. /maze order"), false);
                    }
                }
                level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 0.5F);
            }
        }

        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        long t = clock.phase();
        // Before anything else in the second: the doors closing is the deadline
        // that actually kills people, so it gets said first.
        MazeBell.tick(level, clock);
        MazeRace.tick(level);
        MazeSting.tick(level);
        MazeRaid.tick(level, clock);
        tickEscape(level);
        MazeNight.tickWeather(level);
        GladeBuilder.refreshRoster(level);
        portalAmbience(level, want);
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
    /**
     * The walls moving, made audible.
     *
     * <p>The entire maze rearranged itself at midnight and nothing said so. It is
     * the single most characteristic thing this place does and it happened in
     * total silence, which made the map feel arbitrary rather than alive - you
     * woke up and the route was different, with no moment to attribute it to.
     */
    private static void reshapeHeard(ServerLevel level) {
        reshapeDrama = RESHAPE_DRAMA_TICKS;
        for (ServerPlayer p : level.players()) {
            boolean inside = !MazeData.inGlade(
                    p.blockPosition().getX() / MazeData.CELL,
                    p.blockPosition().getZ() / MazeData.CELL);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal("§4§lTHE WALLS ARE MOVING")));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal(inside
                            ? "§7You are standing in it"
                            : "§8Somewhere out there, everything just changed")));
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.AMBIENT, inside ? 4.0F : 2.0F, 0.3F);
            if (inside) {
                // Being inside the maze while it rearranges around you should not
                // be a sound you hear about afterwards. Six seconds of not being
                // able to trust your own footing is the difference between the
                // reshape happening to the map and happening to you.
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
            }
        }
    }

    /**
     * The eight seconds either side of midnight.
     *
     * <p>The entire maze rearranged itself in silence. It is the single most
     * characteristic thing this place does - the thing the whole map is named
     * for - and it happened with one stone-break sound and an action-bar line
     * you would miss if you were looking at your inventory. A change that large
     * with no announcement does not read as frightening, it reads as a bug.
     *
     * <p>So it is a sequence rather than a sound: a roar to open it, grinding
     * stone laid over hammer-falls for six seconds while the ground you are
     * standing on stops being trustworthy, and a single low note when it stops.
     * Loud in the corridors and distant from the Glade, because the difference
     * between those two places is the point of the Glade.
     */
    private static void tickReshapeDrama(ServerLevel level) {
        if (reshapeDrama <= 0) {
            return;
        }
        int elapsed = RESHAPE_DRAMA_TICKS - reshapeDrama;
        reshapeDrama--;
        RandomSource rng = RandomSource.create();
        for (ServerPlayer p : level.players()) {
            BlockPos at = p.blockPosition();
            boolean inside = !MazeData.inGlade(at.getX() / MazeData.CELL, at.getZ() / MazeData.CELL);
            float loud = inside ? 4.0F : 1.4F;
            if (elapsed % 7 == 0) {
                level.playSound(null, at, SoundEvents.STONE_BREAK, SoundSource.AMBIENT,
                        loud, 0.28F + rng.nextFloat() * 0.16F);
            }
            if (elapsed % 17 == 0) {
                level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.AMBIENT,
                        loud * 0.6F, 0.3F + rng.nextFloat() * 0.1F);
            }
            if (elapsed % 41 == 0) {
                level.playSound(null, at, SoundEvents.WARDEN_ANGRY, SoundSource.AMBIENT,
                        loud * 0.7F, 0.3F);
            }
            if (inside && elapsed % 3 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        at.getX() + rng.nextDouble() * 8 - 4, at.getY() + 3.0,
                        at.getZ() + rng.nextDouble() * 8 - 4, 8, 1.5, 1.0, 1.5, 0.0);
            }
            if (reshapeDrama == 0) {
                level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT,
                        loud * 0.8F, 0.35F);
                p.displayClientMessage(Component.literal(
                        "§8It has settled. §7Nothing is where you left it."), false);
            }
        }
    }

    private static void newDay(ServerLevel level, long day) {
        // The deadline. A Glade that has not found the way out by the end of the
        // last day does not get a ninth - the maze was always going to be the
        // thing that beat you, and an open-ended run lets a competent group live
        // in it forever, which is a settlement simulator rather than this.
        int limit = AbyssConfig.MAZE_DAY_LIMIT.get();
        if (limit > 0 && day >= limit) {
            theEnd(level, "§4§lTHE MAZE TOOK YOU ALL",
                    "§7" + limit + " days was all there was.");
            return;
        }
        MazeRuns.clearAll();
        MazeSting.clearAll();
        NIGHT_OUT.clear();
        // A fresh day, so the bell forgets which second it last rang on.
        MazeBell.reset();
        MazeNight.lift(level);
        TheBox.deliver(level, (int) day);
        // The field comes on overnight. Seeded off the day number so a world that
        // is reloaded mid-run does not get a second harvest out of the same night.
        GladeBuilder.growField(level, RandomSource.create(0xF1E1D ^ day), greenThumb(level));
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
            p.displayClientMessage(Component.literal(
                    "§8Tonight is §c" + String.format("%.1f", Griever.dayScale(level))
                            + "x §8what the first night was."), false);
            if (limit > 0) {
                int left = (int) (limit - day);
                // The last three days say so loudly. A deadline nobody is
                // counting down to is a deadline that arrives as a surprise,
                // and a surprise deadline is just an unfair one.
                p.displayClientMessage(Component.literal(left <= 1
                        ? "§4§lLAST DAY. §7Get out today or not at all."
                        : left <= 3
                                ? "§c⚠ §f" + left + "§c days left."
                                : "§8" + left + " days left."), false);
            }
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.AMBIENT, 0.8F, 1.1F);
        }
    }

    /** Night is everything from the doors sealing to dawn. */
    public static boolean isNight(long timeOfDay) {
        return timeOfDay >= MazeClock.dayTicks();
    }

    /**
     * Per-runner upkeep: brief newcomers, start a clock the moment someone leaves
     * the Glade, finish it if they reach the live exit, and keep the status bar
     * honest.
     */
    private static void tickRunners(ServerLevel level, long t) {
        MazeData.Layout layout = todaysLayout(level);
        // Escapees are collected and sent home after the loop, never inside it.
        // Going through the portal calls changeDimension, which removes the player
        // from the very list being walked here - so reaching the exit threw a
        // ConcurrentModificationException out of the server tick and took the game
        // down with it. Finding the way out crashed the game, which is the worst
        // possible moment for it to happen.
        //
        // The copy is belt to that brace: anything else that ever removes a player
        // mid-loop is now survivable rather than fatal.
        java.util.List<ServerPlayer> escaped = new ArrayList<>();
        java.util.List<Integer> escapedSeconds = new ArrayList<>();
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            brief(p);
            // Nobody moves until they have said what they are, and nobody is
            // sent out into that with an empty inventory.
            MazeInduction.tick(level, p);
            BlockPos at = p.blockPosition();
            int cellX = at.getX() / MazeData.CELL;
            int cellZ = at.getZ() / MazeData.CELL;
            boolean inGlade = MazeData.inGlade(cellX, cellZ);

            // Charting: every corridor cell you stand in is knowledge the Glade
            // keeps. The base graph never moves, so this is durable rather than
            // a note about tonight.
            if (!inGlade && level.getServer() != null) {
                MazeCharts charts = MazeCharts.get(level.getServer());
                MazeJobs jobs = MazeJobs.get(level);
                // A Runner is the only job paid for walking the corridors, and
                // only for ground they had not already walked - otherwise the
                // fastest way to level is to pace one junction all afternoon.
                if (!charts.chartedBy(p.getUUID(), cellX, cellZ)
                        && jobs.is(p.getUUID(), MazeJobs.RUNNER)) {
                    jobs.award(p, MazeJobs.RUNNER, jobs.level(p.getUUID()) >= 2 ? 3 : 2);
                }
                // Cartographer: a Runner with an eye for it takes in the cells
                // around them rather than only the one under their feet. This is
                // the skill the Chart Floor exists to display, so it is the one
                // that most changes what the Glade collectively knows.
                int eye = MazeSkills.rankOf(level, p.getUUID(), "cartographer");
                for (int ox = -eye; ox <= eye; ox++) {
                    for (int oz = -eye; oz <= eye; oz++) {
                        if (ox != 0 || oz != 0) {
                            MazeNotes.record(level, p, cellX + ox, cellZ + oz);
                        }
                    }
                }
                runnersLegs(level, p, jobs);
                bearings(level, p, jobs);
                groundCovered(level, p, jobs, at);
                // Noted, not charted. It reaches the Glade's map when the runner
                // does - and falls on the floor if they do not. The day's work
                // and the "the Glade has charted N%" milestone are both paid at
                // that same moment, for the same reason: a survey nobody brought
                // home did not happen.
                MazeNotes.record(level, p, cellX, cellZ);
            }

            if (inGlade) {
                // Through the door. Everything the trip earned settles here and
                // nowhere else - the job experience, and the survey.
                MazeJobs.get(level).bank(p);
                MazeNotes.bank(level, p);
                MazeNotes.redeemCarried(level, p);
            }
            if (!inGlade && !MazeRuns.isRunning(p.getUUID())) {
                MazeRuns.begin(level, p);
                MazeAdvancements.grant(p, MazeAdvancements.ROOT);
                MazeAdvancements.grant(p, MazeAdvancements.FIRST_RUN);
                veteranHint(level, p, layout);
            }
            // Still out there when the doors shut, and still out there at dawn.
            if (!inGlade && isNight(t)) {
                NIGHT_OUT.add(p.getUUID());
            } else if (!isNight(t) && NIGHT_OUT.remove(p.getUUID())) {
                MazeAdvancements.grant(p, MazeAdvancements.SURVIVE_NIGHT);
            }
            tickLastStand(level, layout, p);
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
                openTheWayOut(level, p);
                // Out is home. Getting through the portal banks the trip exactly
                // as walking back into the Glade would - the job experience, the
                // survey, and any notes recovered on the way. It banked only the
                // experience before, which silently stranded a survey that the
                // rule says was earned: the reward is for surviving, and an
                // escape is the definition of surviving.
                MazeJobs.get(level).bank(p);
                MazeNotes.bank(level, p);
                MazeNotes.redeemCarried(level, p);
                escapees.add(p.getGameProfile().getName());
                escaped.add(p);
                escapedSeconds.add(seconds);
                // No bar for somebody who is on their way out of the dimension.
                continue;
            }
            updateBar(level, p, layout, t);
        }
        for (int i = 0; i < escaped.size(); i++) {
            sendHome(level, escaped.get(i), escapedSeconds.get(i));
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
     * A Runner's legs.
     *
     * <p>Only outside the Glade, because the perk is about the corridors rather
     * than about being a Runner - a Runner pottering round the firepit is just a
     * Glader. Reapplied every second at two seconds' duration so it lapses on
     * its own the moment they step back inside, with no bookkeeping to get wrong.
     */
    private static void runnersLegs(ServerLevel level, ServerPlayer p, MazeJobs jobs) {
        if (!jobs.is(p.getUUID(), MazeJobs.RUNNER)) {
            return;
        }
        int stride = MazeSkills.rankOf(level, p.getUUID(), "stride");
        // Capped at Speed II however you get there. Speed III plus sprinting is
        // faster than a Griever by a margin that makes the night stop mattering,
        // and the maze has to keep being the thing that beats you.
        int amplifier = Math.min(1, (jobs.level(p.getUUID()) >= 3 ? 1 : 0) + (stride >= 2 ? 1 : 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, amplifier, false, false));

        // Second Wind. Once a day, and only when it is genuinely going wrong.
        if (stride >= 3 && p.getHealth() <= 6.0F
                && secondWind.getOrDefault(p.getUUID(), -1) != MazeClock.get(level).day()) {
            secondWind.put(p.getUUID(), MazeClock.get(level).day());
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, true));
            p.displayClientMessage(Component.literal(
                    "§b✦ Second wind. §7Run."), false);
        }

    }

    /**
     * A Runner's sense of where they are.
     *
     * <p>Deliberately the least powerful thing on the sheet, and chosen to be:
     * the last skill here handed out saturation on a timer, which quietly
     * undercut the Box, the field and the larder all at once. This gives away
     * nothing the maze is keeping from you. It tells you where you are standing,
     * which is the one fact a person who runs this place for a living would
     * simply <em>have</em> - and it never tells you where the exit is, because
     * that is the entire job and no skill should shortcut it.
     *
     * <p>The section names are already banded into the corridor walls in colour,
     * so rank one only saves you looking up. Rank three pairs with the Griever
     * holes: they are fixed and chartable, so a veteran would eventually know
     * where they are anyway, and this is that knowledge arriving a fortnight
     * early rather than knowledge nobody could have had.
     */
    private static void bearings(ServerLevel level, ServerPlayer p, MazeJobs jobs) {
        if (!jobs.is(p.getUUID(), MazeJobs.RUNNER)) {
            return;
        }
        int rank = MazeSkills.rankOf(level, p.getUUID(), "bearings");
        if (rank <= 0 || level.getGameTime() % 40L != 0L) {
            return;
        }
        // The Changing owns the action bar while it is running. A compass reading
        // written over a death countdown is the wrong information at the worst
        // possible moment.
        if (MazeSting.isInfected(p.getUUID())) {
            return;
        }
        BlockPos at = p.blockPosition();
        StringBuilder line = new StringBuilder("§7").append(section(at));
        if (rank >= 2) {
            int dx = MazeData.SPAWN_X - at.getX();
            int dz = MazeData.SPAWN_Z - at.getZ();
            int away = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            line.append(" §8| §fGlade ").append(compass(dx, dz)).append(" §7").append(away).append("m");
        }
        if (rank >= 3) {
            BlockPos hole = GrieverHoles.nearest(at);
            int d = (int) Math.sqrt(hole.distSqr(at));
            if (d <= 24) {
                line.append(" §8| §c\u2620 hole ").append(d).append("m");
            }
        }
        p.displayClientMessage(Component.literal(line.toString()), true);
    }

    /** Eight points, which is as precise as a person without an instrument gets. */
    private static String compass(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz) * 2) {
            return dx > 0 ? "E" : "W";
        }
        if (Math.abs(dz) > Math.abs(dx) * 2) {
            return dz > 0 ? "S" : "N";
        }
        return (dz > 0 ? "S" : "N") + (dx > 0 ? "E" : "W");
    }

    /**
     * Paying a Runner for ground.
     *
     * <p>The job was only ever paid for cells it had never seen, which is
     * backwards: a Runner's actual work is covering distance, and somebody
     * sprinting a known route to check whether it still goes through was earning
     * nothing at all. Charting pays for discovery; this pays for the legs.
     */
    private static void groundCovered(ServerLevel level, ServerPlayer p, MazeJobs jobs, BlockPos at) {
        if (!jobs.is(p.getUUID(), MazeJobs.RUNNER)) {
            return;
        }
        BlockPos last = lastSeen.put(p.getUUID(), at);
        if (last == null) {
            return;
        }
        double moved = Math.sqrt(last.distSqr(at));
        // A teleport is not a run. Anything over one cell in a second is the
        // portal, a recall or an operator, and none of those are work.
        if (moved > MazeData.CELL * 2) {
            return;
        }
        double total = walked.merge(p.getUUID(), moved, Double::sum);
        if (total >= 100.0) {
            walked.put(p.getUUID(), total - 100.0);
            jobs.award(p, MazeJobs.RUNNER, 1);
        }
    }

    /**
     * What a Runner at the top of the trade can tell at the gate.
     *
     * <p>Not the route and not the cell - the compass section, which is the same
     * unit the corridor walls are banded in and the same unit the Map Room
     * reports. It removes a quarter of the day's guesswork for somebody who has
     * earned it, and it is still four hundred cells of maze to walk.
     */
    private static void veteranHint(ServerLevel level, ServerPlayer p, MazeData.Layout layout) {
        if (layout == null) {
            return;
        }
        MazeJobs jobs = MazeJobs.get(level);
        if (!jobs.is(p.getUUID(), MazeJobs.RUNNER) || jobs.level(p.getUUID()) < MazeJobs.MAX_LEVEL) {
            return;
        }
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null) {
            return;
        }
        String where = section(new BlockPos(
                ex.cellX() * MazeData.CELL, MazeData.FLOOR_Y + 1, ex.cellZ() * MazeData.CELL));
        p.displayClientMessage(Component.literal(
                "§b✦ You have run this place long enough. §7Today's way out is in the §f"
                        + where + "§7."), false);
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

        // And the real thing, at the far end of the annex. The doorway now means
        // "through here" rather than "you win", so it needs something visible at
        // the other end of the lane for it to be pointing at.
        BlockPos portal = PortalAnnex.portalPos(ex);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                portal.getX() + 0.5, portal.getY() + 1.5, portal.getZ() + 0.5,
                60, 0.5, 1.8, 0.5, 0.4);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                portal.getX() + 0.5, portal.getY() + 2.0, portal.getZ() + 0.5,
                8, 0.3, 1.4, 0.3, 0.01);
        level.playSound(null, portal, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 1.2F, 0.9F);
    }

    /** Within a couple of blocks of today's exit portal. */
    /**
     * Standing on the way out.
     *
     * <p>Measured at the portal in the annex chamber rather than at the doorway
     * in the maze wall. Finding the exit and taking it used to be the same
     * instant, so the whole search had no climax - the hardest thing you ever
     * did was the walking. Now the doorway is where the last fight starts.
     */
    private static boolean atExit(BlockPos at, MazeData.Layout layout) {
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null) {
            return false;
        }
        BlockPos p = PortalAnnex.portalPos(ex);
        return Math.abs(at.getX() - p.getX()) <= 2 && Math.abs(at.getZ() - p.getZ()) <= 2
                && Math.abs(at.getY() - p.getY()) <= 3;
    }

    /**
     * The last forty blocks.
     *
     * <p>Sixty of them, standing between the doorway and the portal, spawned the
     * first time anybody sets foot in the lane on a given day. Once per day, not
     * once per entry, so backing out and walking in again is a retreat rather
     * than a reset - and so a squad that gets halfway and loses two people has to
     * decide whether to finish it tonight.
     */
    /**
     * The best Green Thumb in the Glade.
     *
     * <p>The field is one field. It does not grow four times over because four
     * Track-hoes are standing in it, so the bonus is the best of them rather than
     * the sum - which also means a Glade with one good farmer is as well fed as a
     * Glade with three mediocre ones, and the third one is free to do something
     * else.
     */
    private static int greenThumb(ServerLevel level) {
        int best = 0;
        MazeJobs jobs = MazeJobs.get(level);
        for (ServerPlayer p : level.players()) {
            if (jobs.is(p.getUUID(), MazeJobs.TRACKHOE)) {
                best = Math.max(best, MazeSkills.rankOf(level, p.getUUID(), "greenthumb"));
            }
        }
        return best;
    }

    /**
     * The ending the mode never had.
     *
     * <p>Before this a game simply stopped: one person walked through a doorway
     * and vanished, and the other three were left standing in a field with no
     * acknowledgement that anything had concluded. A week of work ended in
     * silence.
     *
     * <p>So it ends out loud, and it ends with an account. What the Glade
     * actually built is the only thing worth reading at the end of a run: how far
     * they got, how much of the maze they had, who walked out and who did not.
     * The numbers were all being kept already and nobody was ever shown them.
     */
    private static void theEnd(ServerLevel level, String headline, String because) {
        MazeClock clock = MazeClock.get(level);
        MazeCharts charts = MazeCharts.get(level.getServer());
        MazeJobs jobs = MazeJobs.get(level);

        // Collected first, killed after. Hurting a player inside a loop over
        // level.players() is the bug this codebase has produced three times.
        List<ServerPlayer> caught = new ArrayList<>(level.players());
        for (ServerPlayer p : caught) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal(headline)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal(because)));
            p.displayClientMessage(Component.literal("§8§m                                        "), false);
            p.displayClientMessage(Component.literal(
                    "§6§lTHE GLADE §8— §f" + (clock.day() + 1) + "§7 days"), false);
            p.displayClientMessage(Component.literal(
                    "§7Charted §f" + charts.gladePercent() + "%§7 of the maze §8("
                            + charts.markCount() + " cells marked)"), false);
            p.displayClientMessage(Component.literal(
                    "§7Larder §f" + jobs.larder()), false);
            p.displayClientMessage(Component.literal(escapees.isEmpty()
                    ? "§4Nobody got out."
                    : "§aOut: §f" + String.join("§7, §f", escapees)), false);
            p.displayClientMessage(Component.literal("§8§m                                        "), false);
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_DEATH,
                    SoundSource.AMBIENT, 2.0F, 0.4F);
        }
        for (ServerPlayer p : caught) {
            p.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        }
        escapeUntil = 0L;
        escapees.clear();
    }

    /**
     * The five minutes after somebody finds the way out.
     *
     * <p>Finding the exit used to end the game for one person and change nothing
     * for anybody else, which makes a co-op mode into four parallel solo ones at
     * the exact moment it should be most together. Now the first person through
     * starts a clock: everyone else has until it runs out, and the maze keeps
     * whoever does not make it.
     *
     * <p>That is the only rule in here that makes another player's success into
     * your problem, and it is the reason the last five minutes of a game will be
     * the part anybody remembers.
     */
    private static void tickEscape(ServerLevel level) {
        if (escapeUntil <= 0L) {
            return;
        }
        long left = escapeUntil - level.getGameTime();
        if (left <= 0) {
            theEnd(level, "§4§lTHE WAY OUT CLOSED",
                    escapees.isEmpty() ? "§7Nobody made it." : "§7You were not fast enough.");
            return;
        }
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        int seconds = (int) (left / 20L);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§6§lTHE WAY OUT §8— §f" + (seconds / 60) + ":"
                            + (seconds % 60 < 10 ? "0" : "") + (seconds % 60)), true);
            // The last thirty seconds get a sound, because a number on the action
            // bar is easy to stop looking at when something is chasing you.
            if (seconds <= 30) {
                level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND,
                        SoundSource.AMBIENT, 0.8F, seconds <= 10 ? 1.8F : 1.2F);
            }
        }
    }

    /** Somebody has found it. Starts the clock, once. */
    private static void openTheWayOut(ServerLevel level, ServerPlayer first) {
        if (escapeUntil > 0L) {
            return;
        }
        int seconds = AbyssConfig.MAZE_ESCAPE_SECONDS.get();
        escapeUntil = level.getGameTime() + seconds * 20L;
        for (ServerPlayer p : level.players()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal("§6§lTHE WAY OUT IS OPEN")));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal("§f" + first.getGameProfile().getName()
                            + "§7 found it — " + (seconds / 60) + " minutes")));
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.AMBIENT, 2.0F, 1.4F);
        }
    }

    private static void clearLastStand(ServerLevel level) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                -PortalAnnex.REACH, MazeData.FLOOR_Y - 16, -PortalAnnex.REACH,
                MazeData.SPAN + PortalAnnex.REACH, MazeData.WALL_TOP_Y + 4,
                MazeData.SPAN + PortalAnnex.REACH);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box,
                m -> m.getPersistentData().getBoolean(LAST_STAND_TAG))) {
            m.discard();
        }
    }

    private static void tickLastStand(ServerLevel level, MazeData.Layout layout, ServerPlayer p) {
        if (layout == null || hordeDay == MazeClock.get(level).day()) {
            return;
        }
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null || !PortalAnnex.inLane(ex, p.blockPosition())) {
            return;
        }
        hordeDay = MazeClock.get(level).day();
        int count = AbyssConfig.MAZE_LAST_STAND.get();
        double scale = Griever.dayScale(level);
        RandomSource rng = RandomSource.create();
        for (int i = 0; i < count; i++) {
            BlockPos spot = PortalAnnex.laneSpot(ex, i, count);
            net.minecraft.world.entity.monster.Zombie z =
                    net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            if (z == null) {
                continue;
            }
            z.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
            z.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                    net.minecraft.world.entity.MobSpawnType.EVENT, null);
            z.setPersistenceRequired();
            var hp = z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(hp.getBaseValue() * scale);
            }
            var dmg = z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (dmg != null) {
                dmg.setBaseValue(dmg.getBaseValue() * scale);
            }
            z.setHealth(z.getMaxHealth());
            z.getPersistentData().putBoolean(LAST_STAND_TAG, true);
            z.setTarget(p);
            level.addFreshEntity(z);
        }
        for (ServerPlayer who : level.players()) {
            who.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal("§4§lTHE LAST STAND")));
            who.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal("§7Forty blocks. §f" + count + "§7 of them. One way through.")));
            level.playSound(null, who.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.HOSTILE, 3.0F, 0.4F);
        }
    }

    /**
     * Dresses the live exit as a portal you can pick out at a distance.
     *
     * <p>It was a gap in a wall with a lantern over it, which at the end of a long
     * corridor looks like every other gap in every other wall. A framed, lit
     * doorway is the one thing in the maze that says <em>this is the way out</em>
     * without a marker on your screen telling you so.
     */
    private static void portalFrame(ServerLevel level, MazeData.Exit ex, BlockPos centre) {
        boolean alongX = "north".equals(ex.facing()) || "south".equals(ex.facing());
        int baseY = centre.getY() - 1;
        for (int side = -2; side <= 2; side++) {
            for (int dy = 0; dy <= 6; dy++) {
                boolean jamb = side == -2 || side == 2;
                boolean head = dy == 0 || dy == 6;
                if (!jamb && !head) {
                    continue;
                }
                BlockPos at = new BlockPos(
                        centre.getX() + (alongX ? side : 0),
                        baseY + dy,
                        centre.getZ() + (alongX ? 0 : side));
                level.setBlock(at, jamb && head
                        ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                        : jamb ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        // Lit from both jambs, and standing on a dais, so it reads as the thing
        // the whole room was built around rather than as a gap in a wall.
        for (int side : new int[]{-2, 2}) {
            level.setBlock(new BlockPos(
                    centre.getX() + (alongX ? side : 0),
                    baseY + 3,
                    centre.getZ() + (alongX ? 0 : side)), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
        for (int a = -1; a <= 1; a++) {
            level.setBlock(new BlockPos(
                    centre.getX() + (alongX ? a : 0),
                    baseY,
                    centre.getZ() + (alongX ? 0 : a)),
                    Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        }
    }

    /**
     * The doorway out of the maze: an arch, not a prize.
     *
     * <p>Plain trim and a light. Whatever stands here has to read as "the maze
     * ends and something else begins", and emphatically not as the way out -
     * the way out is at the far end of the room beyond it.
     */
    private static void doorwayArch(ServerLevel level, MazeData.Exit ex, int[] p) {
        boolean alongX = "north".equals(ex.facing()) || "south".equals(ex.facing());
        for (int side = -1; side <= 2; side++) {
            for (int dy = -1; dy <= 5; dy++) {
                boolean jamb = side == -1 || side == 2;
                boolean head = dy == -1 || dy == 5;
                if (!jamb && !head) {
                    continue;
                }
                level.setBlock(new BlockPos(
                        p[0] + (alongX ? side : 0),
                        MazeData.WALL_BASE_Y + dy,
                        p[2] + (alongX ? 0 : side)),
                        Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
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
        MazeClock clock = MazeClock.get(level);
        int left = clock.secondsLeftInPhase();
        // The countdown is the whole reason for the bar. Knowing the doors seal
        // in four minutes is a decision; knowing it is "day" is not.
        String title = "§fDay §e" + (clock.day() + 1)
                + " §8| §f" + (layout == null ? "?" : layout.name())
                + " §8| " + (doorsOpen
                        ? "§aDOORS OPEN §7" + (left / 60) + "m" + (left % 60) + "s"
                        : "§4SEALED §7" + (left / 60) + "m" + (left % 60) + "s")
                + " §8| §c☠" + String.format("%.1f", Griever.dayScale(level)) + "x"
                + (MazeJobs.carrying(p.getUUID()) > 0
                        ? " §8| §e▲" + MazeJobs.carrying(p.getUUID()) : "")
                + (run >= 0 ? " §8| §b" + MazeRuns.format(run) : "")
                + MazeSting.hudFragment(level, p);
        bar.setName(Component.literal(title));
        bar.setColor(isNight(t) ? BossEvent.BossBarColor.RED
                : doorsOpen ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW);
        bar.setProgress(MazeClock.get(level).progress());
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
        if (loaded.size() < cap && rng.nextInt(Griever.spawnChanceFor(level)) == 0) {
            Griever.spawnNear(level, runners.get(rng.nextInt(runners.size())), rng);
        }
        // What they can hear. Deliberately after targeting and before ambience,
        // so a Griever that heard you is already coming when it makes its noise.
        Griever.hear(level, loaded, runners);
        Griever.ambience(level, loaded, rng);
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
    /** Whether a toggle point stands on ground the Dead Glade has cleared. */
    private static boolean touchesDeadGlade(MazeData.TogglePoint tp) {
        String[] ends = tp.edge().split(">");
        for (String end : ends) {
            String[] parts = end.split(",");
            if (parts.length != 2) {
                continue;
            }
            try {
                if (DeadGlade.coversCell(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // A malformed edge key is the dataset's problem, not the
                // reshape's; leave it to the toggle itself to fall over loudly.
            }
        }
        return false;
    }

    public static void applyLayout(ServerLevel level, MazeData.Layout layout) {
        if (layout == null) {
            return;
        }
        RandomSource rng = RandomSource.create();
        for (MazeData.TogglePoint tp : MazeData.togglePoints().values()) {
            if (touchesDeadGlade(tp)) {
                // The camp is a clearing. Its walls came down long before you
                // got here and the reshape does not put them back.
                continue;
            }
            MazeBuilder.setToggle(level, tp, layout.open().contains(tp.id()), rng);
        }
        // The camp does not move; the way into it does. Seals its wall and cuts
        // tonight's breaches, chosen from the ones that actually open onto
        // corridor a runner can reach.
        DeadGlade.applyBreaches(level, layout);
        // The exit moves every night, and the sixty standing in front of
        // yesterday's would otherwise stay in yesterday's lane for the life of
        // the world - sixty more every day, in an annex nobody will ever visit
        // again. They belong to a day, so they go when the day does.
        clearLastStand(level);
        openExit(level, layout);
        // Every day, from all four doors, a route is cut to that day's exit.
        // Not decoration: the toggles are drawn from a dataset and nothing in
        // that file promises the way out is reachable, so this is the guarantee.
        guaranteeRoutes(level, layout);
    }

    /**
     * Guarantees every door can reach today's exit, by separate routes.
     *
     * <p>Nothing else in the maze checks that the exit is reachable. The dataset's
     * connectivity plus a night's toggles could perfectly well leave a runner in a
     * maze with no solution at all, and there would be no symptom other than
     * somebody wandering until the clock ran out.
     *
     * <h2>Ask before cutting</h2>
     *
     * <p>This used to carve unconditionally, four routes from four doors, every
     * single day. Two things were wrong with that and they hid each other.
     *
     * <p>The first: the carve never once arrived. Every exit sits on the rim -
     * at cell 0 or cell 95 - and the walk refused to step onto {@code GRID - 1}
     * or below {@code 1}, so it stopped a cell short of its destination on all
     * twenty-eight door-and-preset combinations. The promise in this method's
     * name had never been kept, and nothing noticed because the dataset happened
     * to connect anyway.
     *
     * <p>The second, and the expensive one: on its way to not arriving it cut a
     * near-direct corridor most of the way across the maze. Four of them. That
     * turned a 1,600 to 2,700 block journey into a 250 to 550 block one - the
     * maze was between three and six times smaller than it was drawn to be, and
     * the reason it felt crossable in a single day was a bug.
     *
     * <p>So: check first, carve only if the check fails. All seven presets
     * connect on their own, so in practice this now cuts nothing at all and the
     * maze is the size it was authored to be. A fallback that never fires is the
     * only kind worth having - but it is still here, and it will still fire, if
     * somebody ever writes a preset that strands a door.
     */
    private static void guaranteeRoutes(ServerLevel level, MazeData.Layout layout) {
        MazeData.Exit ex = MazeData.exit(layout.exit());
        if (ex == null) {
            return;
        }
        int salt = 1;
        for (int[] door : MazeBuilder.DOOR_CELLS) {
            // Seventy per cent of the way, always. This is the beaten path the
            // Runners have worn out of the Glade over the weeks they have been
            // here - not a road to the exit, which moves every night.
            //
            // Without it the shortest route to the portal is 1,700 to 2,700
            // blocks: fifty-one to eighty-one per cent of a ten-minute day at a
            // perfect sprint, one way, on a route you would have to already
            // know. That is not a hard maze, it is an unwinnable one, and it is
            // what removing this left behind. With it the run is 400 to 1,050
            // blocks and a round trip fits inside two thirds of a day.
            MazeBuilder.carveRoute(level, door[0], door[1], ex.cellX(), ex.cellZ(),
                    layout.name().hashCode() + salt * 7919, 0.70);
            // And if the last stretch still does not join up, cut the rest.
            if (!MazeData.exitReachable(layout, door[0], door[1])) {
                // Say so. A preset that cannot be solved from one of its own
                // doors is an authoring mistake, and silently papering over it
                // is how it would survive to the next seven presets.
                for (ServerPlayer p : level.players()) {
                    if (p.hasPermissions(2)) {
                        p.displayClientMessage(Component.literal(
                                "§e⚠ " + layout.name() + ": door " + salt
                                        + " could not reach " + layout.exit()
                                        + " — a route was cut for it."), false);
                    }
                }
                MazeBuilder.carveRoute(level, door[0], door[1], ex.cellX(), ex.cellZ(),
                        layout.name().hashCode() + salt * 7919);
            }
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
                // A doorway here, and the portal fifty-four blocks further on at
                // the back of the chamber.
                //
                // The dressed frame used to stand right here, in the maze wall.
                // It looked exactly like the way out, so people walked up to it,
                // stopped, and never went down the lane at all - which is also
                // why nobody ever saw the sixty standing in it. Two symptoms,
                // one cause: the prize was drawn at the entrance to the room it
                // is supposed to be at the back of.
                doorwayArch(level, ex, p);
                portalFrame(level, ex, PortalAnnex.portalPos(ex));
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
        MazeClock c = MazeClock.get(level);
        return "Day " + (c.day() + 1)
                + " | layout " + (l == null ? "?" : l.name())
                + " | exit " + (l == null ? "?" : l.exit())
                + " | " + (doorsOpen ? "doors OPEN" : "doors SEALED")
                + " | " + c.secondsLeftInPhase() + "s left"
                + " | danger " + String.format("%.1f", Griever.dayScale(level)) + "x"
                + " | game #" + (c.session() + 1)
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
