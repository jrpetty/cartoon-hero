package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What you get better at, as opposed to what you are.
 *
 * <p>Jobs answered "what do you do here". They did not answer "what have you
 * become", and those are different questions: two Runners who have both been at
 * it a fortnight were identical, which makes a fortnight worth nothing.
 *
 * <h2>Points come from doing the job, not from time</h2>
 *
 * <p>Track-hoes earn by harvesting, Med-jacks by treating people, Builders by
 * making things and marking corridors, Runners by charting and by <em>distance
 * covered</em> - which is the one that matters most, because a Runner's actual
 * job is ground, and the game was only ever paying them for ground that was new.
 * A Runner doing a known route fast was earning nothing, which is exactly
 * backwards from what a Glade wants its Runners doing.
 *
 * <p>Each job converts its own experience at its own rate, because the jobs
 * generate wildly different numbers - a Med-jack might treat six people in a
 * game and a Track-hoe pulls three hundred carrots. Normalising here is what
 * stops one job being the obvious one to grind.
 *
 * <h2>Why nothing here makes you strong</h2>
 *
 * <p>Every buff is deliberately sideways rather than upward. Nothing raises your
 * health, nothing lets you fight a Griever, nothing shortens the maze. They make
 * you better at your <em>job</em> - a Cartographer charts wider, a Forgemaster's
 * tools last longer, a Med-jack buys more seconds. The maze has to stay the thing
 * that beats you, or the whole place stops meaning anything by day ten.
 */
public final class MazeSkills extends SavedData {

    public static final String NAME = "aztecabyss_maze_skills";

    public static final int MAX_RANK = 3;

    /** A skill: which job it belongs to, what it is called, and what it does. */
    public record Skill(String id, String job, String display, String[] ranks) {
    }

    private static final List<Skill> SKILLS = List.of(
            new Skill("stride", MazeJobs.RUNNER, "Stride", new String[]{
                    "Speed I in the corridors whatever your rank.",
                    "Speed II in the corridors.",
                    "Second Wind: once a day, dropping below three hearts out there gives you ten seconds of speed and regeneration."}),
            new Skill("cartographer", MazeJobs.RUNNER, "Cartographer", new String[]{
                    "You chart the cells either side of you, not just the one you stand in.",
                    "You chart everything within two cells.",
                    "You chart everything within three cells."}),
            new Skill("bearings", MazeJobs.RUNNER, "Bearings", new String[]{
                    "You always know which section of the maze you are standing in.",
                    "And which way the Glade is, and how far.",
                    "And a warning when you come within twenty-odd blocks of a Griever hole."}),
            new Skill("chartwright", MazeJobs.RUNNER, "Chartwright", new String[]{
                    "Pin a living copy of your Runner's Chart to the chart table, for everyone.",
                    "The Box adds two waypoint torches to your kit at dawn.",
                    "Planting a waypoint torch charts the cells around it as if walked."}),

            new Skill("forgemaster", MazeJobs.BUILDER, "Forgemaster", new String[]{
                    "+15% durability on what you forge, over the usual 40%.",
                    "+30%.",
                    "+45%."}),
            new Skill("sharpening", MazeJobs.BUILDER, "Sharpening", new String[]{
                    "+5% damage on what you forged, over the usual 20%.",
                    "+10%.",
                    "+15%."}),
            new Skill("salvage", MazeJobs.BUILDER, "Salvage", new String[]{
                    "A one in six chance of getting a second block back when you take one down in the Glade.",
                    "One in four.",
                    "One in three."}),

            new Skill("dressing", MazeJobs.MEDJACK, "Field Dressing", new String[]{
                    "Your bandages mend one more heart.",
                    "Two more.",
                    "Three more."}),
            new Skill("steady", MazeJobs.MEDJACK, "Steady Hands", new String[]{
                    "Treat from two blocks further, and buy ten more seconds.",
                    "Four blocks, twenty seconds.",
                    "Six blocks, thirty seconds."}),
            new Skill("antivenom", MazeJobs.MEDJACK, "Antivenom", new String[]{
                    "It takes one more sting to turn you.",
                    "Two more.",
                    "Three more."}),

            new Skill("greenthumb", MazeJobs.TRACKHOE, "Green Thumb", new String[]{
                    "The field comes on further overnight.",
                    "Further still.",
                    "The field is never idle."}),
            new Skill("bountiful", MazeJobs.TRACKHOE, "Bountiful", new String[]{
                    "A one in four chance of a third harvest on top of your double.",
                    "One in three.",
                    "One in two."}),
            new Skill("provisions", MazeJobs.TRACKHOE, "Provisions", new String[]{
                    "Rations draw one more loaf.",
                    "Two more.",
                    "Three more, and they cost the larder one less."})
    );

    public static List<Skill> all() {
        return SKILLS;
    }

    public static List<Skill> forJob(String job) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : SKILLS) {
            if (s.job().equals(job)) {
                out.add(s);
            }
        }
        return out;
    }

    public static Skill byId(String id) {
        String want = id.toLowerCase(Locale.ROOT);
        for (Skill s : SKILLS) {
            if (s.id().equals(want)) {
                return s;
            }
        }
        return null;
    }

    /**
     * How much of a job's experience buys one point.
     *
     * <p>Tuned off what each job actually generates rather than off a single
     * number. A Med-jack treating somebody is a rare, deliberate, dangerous act
     * worth eight; a Track-hoe pulling a carrot is worth one and they will pull
     * hundreds. Charging both the same rate would mean nobody ever chose the
     * Med-jack.
     */
    public static int costPerPoint(String job) {
        return switch (job) {
            case MazeJobs.RUNNER -> 55;
            case MazeJobs.BUILDER -> 40;
            case MazeJobs.MEDJACK -> 30;
            case MazeJobs.TRACKHOE -> 50;
            default -> 50;
        };
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Key {@code <uuid>|<skill>} to rank. */
    private final Map<String, Integer> ranks = new HashMap<>();
    /** Key {@code <uuid>|<job>} to points already spent in that job. */
    private final Map<String, Integer> spent = new HashMap<>();

    private static String key(UUID who, String what) {
        return who + "|" + what;
    }

    public int rank(UUID who, String skillId) {
        return ranks.getOrDefault(key(who, skillId), 0);
    }

    /** Convenience for the hooks, which nearly always want "the rank, or zero". */
    public static int rankOf(ServerLevel level, UUID who, String skillId) {
        return get(level).rank(who, skillId);
    }

    public int earned(MazeJobs jobs, UUID who, String job) {
        return jobs.xpOf(who, job) / costPerPoint(job);
    }

    public int spent(UUID who, String job) {
        return spent.getOrDefault(key(who, job), 0);
    }

    public int available(MazeJobs jobs, UUID who, String job) {
        return Math.max(0, earned(jobs, who, job) - spent(who, job));
    }

    /**
     * Spends a point.
     *
     * @return null on success, or why not
     */
    public String learn(MazeJobs jobs, UUID who, Skill skill) {
        if (!jobs.is(who, skill.job())) {
            return "That is not your trade.";
        }
        int have = rank(who, skill.id());
        if (have >= MAX_RANK) {
            return "You have taken that as far as it goes.";
        }
        if (available(jobs, who, skill.job()) < 1) {
            return "Nothing spare to spend. Do the work.";
        }
        ranks.put(key(who, skill.id()), have + 1);
        spent.merge(key(who, skill.job()), 1, Integer::sum);
        setDirty();
        return null;
    }

    /**
     * Puts every point in a job back.
     *
     * <p>Free, and on purpose. Points are earned by doing the job for hours; a
     * system where a misread tooltip costs you those hours is a system people
     * refuse to experiment with, and twelve skills nobody experiments with is
     * three skills.
     */
    public int forget(UUID who, String job) {
        int back = 0;
        for (Skill s : forJob(job)) {
            Integer had = ranks.remove(key(who, s.id()));
            if (had != null) {
                back += had;
            }
        }
        spent.put(key(who, job), 0);
        setDirty();
        return back;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag r = new CompoundTag();
        ranks.forEach(r::putInt);
        tag.put("Ranks", r);
        CompoundTag s = new CompoundTag();
        spent.forEach(s::putInt);
        tag.put("Spent", s);
        return tag;
    }

    public static MazeSkills load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeSkills out = new MazeSkills();
        CompoundTag r = tag.getCompound("Ranks");
        for (String k : r.getAllKeys()) {
            out.ranks.put(k, r.getInt(k));
        }
        CompoundTag s = tag.getCompound("Spent");
        for (String k : s.getAllKeys()) {
            out.spent.put(k, s.getInt(k));
        }
        return out;
    }

    public static SavedData.Factory<MazeSkills> factory() {
        return new SavedData.Factory<>(MazeSkills::new, MazeSkills::load, null);
    }

    public static MazeSkills get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static MazeSkills get(ServerLevel level) {
        return get(level.getServer());
    }
}
