package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Jobs, and the Glade's reason to exist.
 *
 * <p>The Glade was a waiting room. It has a homestead, three huts, a worked
 * field, a firepit, a wood and a burial grove, and until now a player could do
 * precisely nothing with any of it - you stood in it until the doors opened and
 * then you left. Every hour of the day cycle that was not "inside the maze" was
 * dead time, which is a strange thing for a game whose whole structure is
 * <em>half the clock is spent in here</em>.
 *
 * <p>The fix is not more scenery. It is that the Glade is a settlement and a
 * settlement is people doing different things. Four jobs, each one hooked into
 * something the map already had and was refusing to use:
 *
 * <ul>
 *   <li><b>Runner</b> - the maze is your ground. You move faster in it and you
 *       are the only job paid for charting it. Hooks the charting the Map Room
 *       already keeps.</li>
 *   <li><b>Builder</b> - everything you craft comes out tougher and sharper, and
 *       you may mark the corridors with more than a torch. Hooks the crafting
 *       bench and the placement rule, which banned everything and so banned the
 *       one thing a mapmaker needs.</li>
 *   <li><b>Med-jack</b> - you can buy a stung Runner time, and eventually pull
 *       them back. Hooks the Changing, which until now nobody but the victim
 *       could do anything about.</li>
 *   <li><b>Track-hoe</b> - every farmable block gives you twice what it gives
 *       anybody else, and the field feeds the Glade. Hooks the farmland that was
 *       drawn in and then left as decoration.</li>
 * </ul>
 *
 * <h2>Why levels</h2>
 *
 * <p>Day twelve played exactly like day three. The maze reshapes nightly but
 * nothing about <em>you</em> was different for having survived a week, so the
 * day counter was a label rather than a progression. A job that gets better the
 * more of it you do gives the days somewhere to accumulate, and it does so
 * without power-creeping the maze itself: a level 4 Runner is faster and better
 * informed, not harder to kill.
 *
 * <h2>Why experience is kept per job</h2>
 *
 * <p>Switching jobs is free and costs you nothing you earned. A Glade where
 * changing your mind burns a week of work is a Glade where nobody ever tries
 * the other three, which would leave three quarters of this unread.
 */
public final class MazeJobs extends SavedData {

    public static final String NAME = "aztecabyss_maze_jobs";

    public static final String RUNNER = "runner";
    public static final String BUILDER = "builder";
    public static final String MEDJACK = "medjack";
    public static final String TRACKHOE = "trackhoe";

    public static final List<String> ALL = List.of(RUNNER, BUILDER, MEDJACK, TRACKHOE);

    /** Experience at which each level begins. Index 0 is level 1. */
    private static final int[] THRESHOLDS = {0, 40, 120, 300};
    public static final int MAX_LEVEL = THRESHOLDS.length;

    /** What each job is called out loud, and its colour. */
    public static String display(String job) {
        return switch (job) {
            case RUNNER -> "§bRunner";
            case BUILDER -> "§6Builder";
            case MEDJACK -> "§aMed-jack";
            case TRACKHOE -> "§2Track-hoe";
            default -> "§7Greenie";
        };
    }

    public static String blurb(String job) {
        return switch (job) {
            case RUNNER -> "§7Faster in the corridors, and the only job paid for charting them.";
            case BUILDER -> "§7Anything you craft lasts 40% longer and hits 20% harder.";
            case MEDJACK -> "§7Buys a stung Runner time. At level 3, pulls them back.";
            case TRACKHOE -> "§7Double drops from every farmable block. The field feeds the Glade.";
            default -> "§7No job yet. §8/maze job <name>";
        };
    }

    public static boolean isJob(String s) {
        return ALL.contains(s.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private final Map<UUID, String> jobs = new HashMap<>();
    /** Key is {@code <uuid>|<job>}, so changing your mind never costs you. */
    private final Map<String, Integer> xp = new HashMap<>();
    /**
     * The Glade's food store.
     *
     * <p>Shared, not per player, because that is the entire point of it: a
     * Track-hoe who spends the morning in the field has done something for four
     * other people, and a Runner drawing rations before a run is spending
     * somebody else's work. It is the only number in the maze that one player
     * can raise and another can lower.
     */
    private int larder = 0;

    private static String key(UUID who, String job) {
        return who + "|" + job;
    }

    public String jobOf(UUID who) {
        return jobs.get(who);
    }

    /** Takes a job. Returns false if they already had it. */
    public boolean setJob(UUID who, String job) {
        String now = jobs.get(who);
        if (job.equals(now)) {
            return false;
        }
        jobs.put(who, job);
        setDirty();
        return true;
    }

    public int xpOf(UUID who, String job) {
        return xp.getOrDefault(key(who, job), 0);
    }

    public int levelOf(UUID who, String job) {
        return levelFor(xpOf(who, job));
    }

    /** The level of the job they are actually doing, or 0 if they have none. */
    public int level(UUID who) {
        String job = jobs.get(who);
        return job == null ? 0 : levelOf(who, job);
    }

    public boolean is(UUID who, String job) {
        return job.equals(jobs.get(who));
    }

    public static int levelFor(int amount) {
        int level = 1;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (amount >= THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    /** Experience still needed for the next level, or -1 at the cap. */
    public int toNext(UUID who, String job) {
        int have = xpOf(who, job);
        int level = levelFor(have);
        if (level >= MAX_LEVEL) {
            return -1;
        }
        return THRESHOLDS[level] - have;
    }

    /**
     * Credits work done. Only ever credits the job they are currently doing, so
     * the numbers mean "time spent being this" rather than "time spent at all".
     *
     * <p>Announces a level as it happens, because a threshold crossed silently
     * is a threshold nobody knows they crossed.
     */
    public void award(ServerPlayer player, String job, int amount) {
        if (amount <= 0 || !is(player.getUUID(), job)) {
            return;
        }
        String k = key(player.getUUID(), job);
        int before = xp.getOrDefault(k, 0);
        int after = before + amount;
        xp.put(k, after);
        setDirty();
        int wasLevel = levelFor(before);
        int nowLevel = levelFor(after);
        if (nowLevel > wasLevel) {
            player.displayClientMessage(Component.literal(
                    display(job) + " §f§lLEVEL " + nowLevel + "§r §7— " + perkLine(job, nowLevel)), false);
        }
    }

    /** What the level you just reached actually gives you. */
    public static String perkLine(String job, int level) {
        return switch (job) {
            case RUNNER -> level >= 4 ? "Speed II, and the day's exit section is named to you."
                    : level >= 3 ? "Speed II in the corridors."
                    : level >= 2 ? "A new cell now pays 3 instead of 2."
                    : "Speed I outside the Glade.";
            case BUILDER -> level >= 4 ? "A marked cell now counts as charted for the whole Glade."
                    : level >= 3 ? "Lanterns and banners as well."
                    : level >= 2 ? "Wool as well as carpet."
                    : "Forged gear: +40% durability, +20% damage. Carpet in the corridors.";
            case MEDJACK -> level >= 4 ? "Curing no longer costs your day."
                    : level >= 3 ? "/maze treat now cures outright, once a day."
                    : level >= 2 ? "/maze treat buys 45 seconds."
                    : "/maze treat buys 30 seconds.";
            case TRACKHOE -> level >= 4 ? "Every crop feeds four."
                    : level >= 3 ? "Every crop feeds three."
                    : level >= 2 ? "Every crop feeds two."
                    : "Double drops from anything farmable; each crop feeds one.";
            default -> "";
        };
    }

    // ------------------------------------------------------------------
    // The larder
    // ------------------------------------------------------------------

    public int larder() {
        return larder;
    }

    public void store(int amount) {
        larder = Math.min(999, larder + Math.max(0, amount));
        setDirty();
    }

    /** Draws from the store. Returns false if there was not enough in it. */
    public boolean draw(int amount) {
        if (larder < amount) {
            return false;
        }
        larder -= amount;
        setDirty();
        return true;
    }

    /** Everyone with a job, for the roster. */
    public List<String> roster(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String job = jobs.get(p.getUUID());
            if (job == null) {
                out.add("§8" + p.getGameProfile().getName() + " §7— no job");
            } else {
                out.add("§f" + p.getGameProfile().getName() + " §7— " + display(job)
                        + " §8lv" + levelOf(p.getUUID(), job));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag j = new CompoundTag();
        jobs.forEach((id, job) -> j.putString(id.toString(), job));
        tag.put("Jobs", j);
        CompoundTag x = new CompoundTag();
        xp.forEach(x::putInt);
        tag.put("Xp", x);
        tag.putInt("Larder", larder);
        return tag;
    }

    public static MazeJobs load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeJobs out = new MazeJobs();
        CompoundTag j = tag.getCompound("Jobs");
        for (String k : j.getAllKeys()) {
            try {
                out.jobs.put(UUID.fromString(k), j.getString(k));
            } catch (IllegalArgumentException ignored) {
                // A key that is not a uuid is not worth losing the rest of the file over.
            }
        }
        CompoundTag x = tag.getCompound("Xp");
        for (String k : x.getAllKeys()) {
            out.xp.put(k, x.getInt(k));
        }
        out.larder = tag.getInt("Larder");
        return out;
    }

    public static SavedData.Factory<MazeJobs> factory() {
        return new SavedData.Factory<>(MazeJobs::new, MazeJobs::load, null);
    }

    public static MazeJobs get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static MazeJobs get(ServerLevel level) {
        return get(level.getServer());
    }

    // ------------------------------------------------------------------
    // The Med-jack's day
    // ------------------------------------------------------------------

    /**
     * The last day each Med-jack spent their cure on.
     *
     * <p>Deliberately not saved with the world. A cooldown measured in in-game
     * days that survives a restart is a cooldown nobody can reason about, and
     * the worst it costs to forget is one extra cure on the day the server came
     * back up.
     */
    private static final Map<UUID, Long> LAST_CURE = new HashMap<>();

    /** True if this Med-jack still has their cure today. */
    public static boolean cureReady(UUID who, long day, int level) {
        if (level >= MAX_LEVEL) {
            return true; // level 4 stops spending the day on it
        }
        Long last = LAST_CURE.get(who);
        return last == null || last != day;
    }

    public static void spendCure(UUID who, long day) {
        LAST_CURE.put(who, day);
    }
}
