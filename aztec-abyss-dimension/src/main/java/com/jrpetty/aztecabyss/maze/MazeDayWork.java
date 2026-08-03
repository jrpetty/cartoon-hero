package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A day's work, and what the Glade pays for it.
 *
 * <p>The supply pool is fifty credits a head and nothing else, which says
 * something the mode does not mean: that turning up is the whole job. A
 * Track-hoe who worked the field until dusk and one who sat by the firepit
 * brought the same amount home. So the pool has a second half - what each
 * person actually did today - and it is deliberately the half you have to earn.
 *
 * <h2>One rule, four trades</h2>
 *
 * <p>Every trade has a quota and every quota is worth the same thirty credits.
 * You are paid the fraction of your own quota you managed, so half a day's work
 * is half the money and there is no cliff to fall off at the end of the day:
 *
 * <pre>credits = 30 x min(1, units / quota)</pre>
 *
 * <p>Equal ceilings on purpose. The moment one trade pays better than another,
 * the trade board stops being a choice about what the Glade needs and becomes a
 * question with a right answer.
 *
 * <h2>What counts, and why it cannot be farmed</h2>
 *
 * <ul>
 *   <li><b>Track-hoe</b> - food produced. Harvested crops and cooked or baked
 *       food both count, and wheat does not, so wheat-into-bread is paid once
 *       rather than twice.
 *   <li><b>Runner</b> - blocks of corridor the Glade had never seen. Measured
 *       in blocks rather than cells because a cell is an invisible unit: a player
 *       sees corridor, not a grid. Each newly charted cell is six blocks of it.
 *       Crucially it is <em>new</em> ground rather than distance walked - pacing
 *       a corridor you already know is the one Runner metric somebody could farm
 *       standing in one place.
 *   <li><b>Med-jack</b> - bandages made, and people treated or revived at three
 *       apiece because a person is worth more than a dressing.
 *   <li><b>Builder</b> - things forged. Not blocks placed: a wall you can put up
 *       and take down forty times is not a day's work, and any metric you can
 *       farm standing still is one somebody will.
 * </ul>
 *
 * <p>Every one of these is bounded by something scarce - seeds, unwalked maze,
 * string and paper, iron. None of them can be ground out on the spot, which is
 * the property that matters.
 */
public final class MazeDayWork extends SavedData {

    public static final String NAME = "aztecabyss_maze_daywork";

    /**
     * Food, for the purposes of a day's farming.
     *
     * <p>An explicit list rather than a component lookup. Wheat is deliberately
     * absent - it is an ingredient, and counting it as well as the bread it
     * becomes would pay a Track-hoe twice for one loaf.
     */
    private static final Set<Item> FOOD = Set.of(
            Items.CARROT, Items.POTATO, Items.BAKED_POTATO, Items.BEETROOT,
            Items.MELON_SLICE, Items.PUMPKIN_PIE, Items.BREAD, Items.COOKIE,
            Items.BEETROOT_SOUP, Items.MUSHROOM_STEW, Items.RABBIT_STEW,
            Items.APPLE, Items.SWEET_BERRIES, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT,
            Items.COOKED_COD, Items.COOKED_SALMON, Items.DRIED_KELP);

    public static boolean isFood(Item item) {
        return FOOD.contains(item);
    }

    /** Units of work each trade needs for a full day's pay. */
    public static int quotaFor(String job) {
        if (job == null) {
            return 0;
        }
        return switch (job) {
            case MazeJobs.TRACKHOE -> AbyssConfig.MAZE_QUOTA_FARM.get();
            case MazeJobs.RUNNER -> AbyssConfig.MAZE_QUOTA_CHART.get();
            case MazeJobs.MEDJACK -> AbyssConfig.MAZE_QUOTA_CARE.get();
            case MazeJobs.BUILDER -> AbyssConfig.MAZE_QUOTA_FORGE.get();
            default -> 0;
        };
    }

    /** What one person's full day is worth. The same for every trade. */
    public static int maxCredits() {
        return AbyssConfig.MAZE_DAY_WORK_CREDITS.get();
    }

    /** Plain English for what this trade is being counted on. */
    public static String unitName(String job) {
        if (job == null) {
            return "work";
        }
        return switch (job) {
            case MazeJobs.TRACKHOE -> "food produced";
            case MazeJobs.RUNNER -> "blocks of new ground";
            case MazeJobs.MEDJACK -> "bandages and patients";
            case MazeJobs.BUILDER -> "things forged";
            default -> "work";
        };
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Units done today, per person. Cleared when the Box comes up. */
    private final Map<UUID, Integer> units = new LinkedHashMap<>();

    public int unitsOf(UUID who) {
        return units.getOrDefault(who, 0);
    }

    /**
     * Records work, but only work of the kind this person's trade is paid for.
     *
     * <p>A Runner who harvests a carrot has done something useful and is paid
     * nothing for it, which is the point: the trade you took is the work the
     * Glade is counting.
     */
    public void add(ServerLevel level, ServerPlayer player, String job, int amount) {
        if (amount <= 0 || job == null) {
            return;
        }
        MazeJobs jobs = MazeJobs.get(level);
        if (!jobs.is(player.getUUID(), job)) {
            return;
        }
        int quota = quotaFor(job);
        if (quota <= 0) {
            return;
        }
        int before = unitsOf(player.getUUID());
        if (before >= quota) {
            // Already full. Stop counting rather than banking overflow that
            // could never be paid - a number that keeps climbing after it has
            // stopped meaning anything is a number people misread.
            return;
        }
        int after = Math.min(quota, before + amount);
        units.put(player.getUUID(), after);
        setDirty();

        int wasCredits = creditsFrom(before, quota);
        int nowCredits = creditsFrom(after, quota);
        if (nowCredits > wasCredits) {
            player.displayClientMessage(Component.literal(
                    "§a✦ §7Day's work §f" + after + "§7/§f" + quota + " §8"
                            + unitName(job) + " §7— §a+" + nowCredits + "§7 to the pool"
                            + (after >= quota ? " §6(full day)" : "")), true);
        }
    }

    private static int creditsFrom(int done, int quota) {
        if (quota <= 0) {
            return 0;
        }
        return (int) Math.round(maxCredits() * Math.min(1.0, done / (double) quota));
    }

    /** What this person's day has earned the Glade so far. */
    public int creditsOf(ServerLevel level, UUID who) {
        String job = MazeJobs.get(level).jobOf(who);
        return creditsFrom(unitsOf(who), quotaFor(job));
    }

    /**
     * Everything everybody has earned today.
     *
     * <p>Counted for people who have since logged off as well. They did the
     * work; the Glade has the food.
     */
    public static int totalCredits(ServerLevel level) {
        MazeDayWork work = get(level);
        MazeJobs jobs = MazeJobs.get(level);
        int total = 0;
        for (Map.Entry<UUID, Integer> e : work.units.entrySet()) {
            total += creditsFrom(e.getValue(), quotaFor(jobs.jobOf(e.getKey())));
        }
        return total;
    }

    /** A new day. Yesterday's effort is yesterday's. */
    public void clearAll() {
        units.clear();
        setDirty();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag done = new CompoundTag();
        units.forEach((id, n) -> done.putInt(id.toString(), n));
        tag.put("Units", done);
        return tag;
    }

    public static MazeDayWork load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeDayWork out = new MazeDayWork();
        CompoundTag done = tag.getCompound("Units");
        for (String id : done.getAllKeys()) {
            try {
                out.units.put(UUID.fromString(id), done.getInt(id));
            } catch (IllegalArgumentException ignored) {
                // Not a uuid. Not worth losing everybody else's day over.
            }
        }
        return out;
    }

    public static SavedData.Factory<MazeDayWork> factory() {
        return new SavedData.Factory<>(MazeDayWork::new, MazeDayWork::load, null);
    }

    public static MazeDayWork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static MazeDayWork get(ServerLevel level) {
        return get(level.getServer());
    }
}
