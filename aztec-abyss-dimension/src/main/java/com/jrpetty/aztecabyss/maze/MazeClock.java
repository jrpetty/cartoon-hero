package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Random;

/**
 * The maze's own clock, and the shape of a single game.
 *
 * <h2>Why the maze needed a clock of its own</h2>
 *
 * <p>Every schedule in here was read straight off {@code level.getDayTime()},
 * which for any dimension that is not the overworld is the <em>overworld's</em>
 * time. The maze was therefore not running on a clock at all - it was running on
 * whatever hour it happened to be somewhere else. Someone sleeping in a bed on
 * the surface skipped the maze's night. An operator typing {@code /time set day}
 * threw the doors open. The day counter counted the overworld's days, so a maze
 * built on Tuesday started on "day 400".
 *
 * <p>Worse, none of it could be tuned. A night is 11500 vanilla ticks and that
 * is that; asking for a ten-minute night was asking for something the code had
 * no way to express, because the length of a night was not a number anywhere.
 * Now it is two numbers in the config and everything else is derived.
 *
 * <h2>How the sky follows</h2>
 *
 * <p>{@code setDayTime} on a non-overworld level is a no-op - the level data is
 * a read-only view of the overworld's - so the sky cannot be moved server-side.
 * Instead each player standing in the maze is sent a time packet every tick with
 * the phase mapped onto a vanilla day, and the daylight-cycle flag turned off so
 * their client holds exactly what it is given rather than drifting on. Step out
 * of the maze and the server's own broadcast puts the real sky back within a
 * second.
 *
 * <h2>What a game is</h2>
 *
 * <p>A game runs from the first person walking in to the last person leaving,
 * however they leave - out through the portal or out in a box. When the maze
 * empties the session is over, and the next one re-rolls which of the seven
 * layouts is day one. They still run in order after that, so a game that opens
 * on Saturday's maze goes Sunday, Monday, Tuesday; the next game might open on
 * Wednesday's. The order is the constant and the entry point is the variable,
 * which is the thing worth remembering about a maze you are meant to learn.
 */
public final class MazeClock extends SavedData {

    public static final String NAME = "aztecabyss_maze_clock";

    /** The last minute of daylight is the warning. */
    private static final int DUSK_WARNING_SECONDS = 60;

    /** Ticks into the current maze day. */
    private int phase;
    /** Which day of this game it is, from zero. */
    private int day;
    /** Which of the seven layouts this game opened on. */
    private int startPreset;
    /** Bumped every time a game ends, so anything cached per game can notice. */
    private int session;
    /** True once somebody has actually been inside during this game. */
    private boolean played;

    // ------------------------------------------------------------------
    // Lengths, all derived from two config numbers
    // ------------------------------------------------------------------

    public static int dayTicks() {
        return AbyssConfig.MAZE_DAY_SECONDS.get() * 20;
    }

    public static int nightTicks() {
        return AbyssConfig.MAZE_NIGHT_SECONDS.get() * 20;
    }

    public static int cycleTicks() {
        return dayTicks() + nightTicks();
    }

    // ------------------------------------------------------------------
    // Reading the clock
    // ------------------------------------------------------------------

    public int phase() {
        return phase;
    }

    public int day() {
        return day;
    }

    public int session() {
        return session;
    }

    public int startPreset() {
        return startPreset;
    }

    public boolean isNight() {
        return phase >= dayTicks();
    }

    /** Seconds left in whichever phase is running. */
    public int secondsLeftInPhase() {
        int end = isNight() ? cycleTicks() : dayTicks();
        return Math.max(0, (end - phase) / 20);
    }

    public boolean inDuskWarning() {
        int d = dayTicks();
        return phase < d && phase >= d - DUSK_WARNING_SECONDS * 20;
    }

    /**
     * Midnight: halfway through the night, and the moment the walls move.
     *
     * <p>Reported as "did we just cross it" rather than "is it now", because the
     * reshape has to fire exactly once and the clock is the only thing that
     * knows how far it moved.
     */
    public boolean crossedMidnight(int advancedBy) {
        int mid = dayTicks() + nightTicks() / 2;
        return phase >= mid && phase - advancedBy < mid;
    }

    /** How far through the whole day we are, for the bar. */
    public float progress() {
        int c = Math.max(1, cycleTicks());
        return Math.max(0.0F, Math.min(1.0F, (float) phase / (float) c));
    }

    // ------------------------------------------------------------------
    // Running it
    // ------------------------------------------------------------------

    /**
     * Moves the clock on. Returns true if the day rolled over.
     *
     * <p>Rollover subtracts a cycle rather than taking a modulus so that
     * changing the config mid-world moves the next day rather than
     * retroactively rewriting which day it is.
     */
    public boolean advance(int by) {
        phase += by;
        setDirty();
        int c = cycleTicks();
        if (phase < c) {
            return false;
        }
        phase -= c;
        if (phase >= c) {
            phase = 0; // config shrank under us; do not spiral
        }
        day++;
        return true;
    }

    /**
     * Notes that somebody is actually here.
     *
     * @return true only on the transition - the moment a game begins. The first
     *         day never had a beginning anybody could hook: the day rollover is
     *         what fired the morning delivery, and day zero has no rollover
     *         before it, so the opening crate was silently never delivered.
     */
    public boolean markPlayed() {
        if (played) {
            return false;
        }
        played = true;
        setDirty();
        return true;
    }

    public boolean played() {
        return played;
    }

    /**
     * Ends the game and sets up the next one.
     *
     * <p>The new starting layout is drawn at random and is allowed to be the same
     * one twice: forcing it to differ would mean seven games in a row could never
     * repeat an opening, which is a pattern players learn faster than randomness.
     */
    public void newGame(MinecraftServer server) {
        session++;
        day = 0;
        phase = 0;
        played = false;
        int n = Math.max(1, MazeData.layouts().size());
        startPreset = new Random(server.overworld().getGameTime() ^ (session * 0x9E3779B9L)).nextInt(n);
        setDirty();
    }

    /** The layout standing on a given day of this game. */
    public MazeData.Layout layoutFor(int whichDay) {
        int n = MazeData.layouts().size();
        if (n == 0) {
            return null;
        }
        return MazeData.layout(startPreset + whichDay);
    }

    // ------------------------------------------------------------------
    // The sky
    // ------------------------------------------------------------------

    /**
     * The phase, expressed as a vanilla day time.
     *
     * <p>Daylight is mapped onto 0 to 12000 and night onto 12000 to 24000
     * whatever the real lengths are, so a ten-minute night and a forty-minute
     * day both give a full sunrise, a full noon and a full sunset - the sky
     * moves at a different speed rather than skipping any of it.
     */
    public long skyTime() {
        int d = Math.max(1, dayTicks());
        int n = Math.max(1, nightTicks());
        if (!isNight()) {
            return (long) (phase / (double) d * 12000.0);
        }
        return 12000L + (long) ((phase - d) / (double) n * 12000.0);
    }

    /**
     * Puts the maze's sky on the screen of everyone standing in it.
     *
     * <p>Every tick, and with the cycle flag off. Sending less often would let
     * the client tick its own day time on between packets at vanilla speed,
     * which is precisely the speed we are overriding, and the sky would stutter
     * once a second.
     */
    public void pushSky(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        ClientboundSetTimePacket packet =
                new ClientboundSetTimePacket(level.getGameTime(), skyTime(), false);
        for (ServerPlayer p : level.players()) {
            p.connection.send(packet);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Phase", phase);
        tag.putInt("Day", day);
        tag.putInt("Start", startPreset);
        tag.putInt("Session", session);
        tag.putBoolean("Played", played);
        return tag;
    }

    public static MazeClock load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeClock c = new MazeClock();
        c.phase = tag.getInt("Phase");
        c.day = tag.getInt("Day");
        c.startPreset = tag.getInt("Start");
        c.session = tag.getInt("Session");
        c.played = tag.getBoolean("Played");
        return c;
    }

    public static SavedData.Factory<MazeClock> factory() {
        return new SavedData.Factory<>(MazeClock::new, MazeClock::load, null);
    }

    public static MazeClock get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static MazeClock get(ServerLevel level) {
        return get(level.getServer());
    }
}
