package com.jrpetty.aztecabyss.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Records, for every map there is.
 *
 * <p>{@link AbyssStats} already kept bests, and kept them keyed by an {@code int}
 * index into the {@code ArenaMap} enum. That was fine while there were three maps
 * and they were all enum constants, and it silently excluded the two kinds of map
 * that have since appeared: the Maze, which is scored in seconds rather than
 * rounds, and published maps, which are files and have no enum index at all.
 *
 * <p>So a map you made recorded nothing. You could build one, play it, and there
 * was no answer to "how far did you get" - which is most of the reason to play a
 * map twice, and all of the reason to play someone else's.
 *
 * <p>Keyed by string here, so anything that can name itself can have a record:
 * {@code temple}, {@code maze}, {@code custom:blood_harbour}.
 *
 * <h2>Solo and group are different games</h2>
 *
 * <p>Kept as two boards rather than one with a column, because they are not
 * comparable. Four people reach round thirty on ground one person could not hold
 * past twelve, and a solo runner who beats the maze did something a group of five
 * did not do. Putting them in one table makes the solo column decorative.
 */
public final class Leaderboards extends SavedData {

    public static final String NAME = "aztecabyss_leaderboards";

    /** How a board is sorted, which depends on what the map is asking of you. */
    public enum Mode {
        /** Higher is better: rounds survived. */
        ROUNDS,
        /** Lower is better: seconds taken. */
        TIME
    }

    /** How many places each board keeps. */
    private static final int KEEP = 25;

    /** One entry on one board. */
    public record Row(UUID player, String name, int score, int seconds, int party, long when) {
    }

    /** map key -> solo rows. */
    private final Map<String, List<Row>> solo = new LinkedHashMap<>();
    /** map key -> group rows. */
    private final Map<String, List<Row>> group = new LinkedHashMap<>();
    /** map key -> how it is scored. */
    private final Map<String, Mode> modes = new LinkedHashMap<>();

    public Leaderboards() {
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * Offers a result to a board. Keeps it only if it beats what that player had.
     *
     * <p>One row per player per board, on purpose. A leaderboard where the same
     * name holds the top six places is not a leaderboard, it is somebody's
     * personal history - and it pushes everyone else off the only screen anybody
     * reads.
     *
     * @param party how many were in it together; 1 goes to solo, more to group
     * @return true if this changed the board
     */
    public boolean submit(String mapKey, Mode mode, UUID player, String name,
                          int score, int seconds, int party) {
        String key = mapKey.toLowerCase(Locale.ROOT);
        modes.put(key, mode);
        Map<String, List<Row>> board = party > 1 ? group : solo;
        List<Row> rows = board.computeIfAbsent(key, k -> new ArrayList<>());

        Row existing = null;
        for (Row r : rows) {
            if (r.player().equals(player)) {
                existing = r;
                break;
            }
        }
        Row candidate = new Row(player, name, score, seconds, party, System.currentTimeMillis());
        if (existing != null) {
            if (!better(mode, candidate, existing)) {
                return false;
            }
            rows.remove(existing);
        }
        rows.add(candidate);
        rows.sort(comparator(mode));
        while (rows.size() > KEEP) {
            rows.remove(rows.size() - 1);
        }
        setDirty();
        return true;
    }

    private static boolean better(Mode mode, Row a, Row b) {
        return mode == Mode.TIME ? a.seconds() < b.seconds() : a.score() > b.score();
    }

    private static Comparator<Row> comparator(Mode mode) {
        return mode == Mode.TIME
                ? Comparator.comparingInt(Row::seconds)
                // Rounds descending, and a tie broken by who did it faster - two
                // people who both reached round twenty did not do the same thing
                // if one took half as long.
                : Comparator.comparingInt(Row::score).reversed()
                        .thenComparingInt(Row::seconds);
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public Mode modeOf(String mapKey) {
        return modes.getOrDefault(mapKey.toLowerCase(Locale.ROOT), Mode.ROUNDS);
    }

    public List<Row> top(String mapKey, boolean isGroup, int limit) {
        List<Row> rows = (isGroup ? group : solo)
                .getOrDefault(mapKey.toLowerCase(Locale.ROOT), List.of());
        return rows.subList(0, Math.min(limit, rows.size()));
    }

    /** Where this player stands, 1-based, or 0 if they are not on it. */
    public int placeOf(String mapKey, boolean isGroup, UUID player) {
        List<Row> rows = (isGroup ? group : solo)
                .getOrDefault(mapKey.toLowerCase(Locale.ROOT), List.of());
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).player().equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Every map key that has any record at all. */
    public List<String> mapsWithRecords() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(solo.keySet());
        keys.addAll(group.keySet());
        return new ArrayList<>(keys);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Solo", writeBoards(solo));
        tag.put("Group", writeBoards(group));
        ListTag modeList = new ListTag();
        modes.forEach((k, v) -> {
            CompoundTag one = new CompoundTag();
            one.putString("Key", k);
            one.putString("Mode", v.name());
            modeList.add(one);
        });
        tag.put("Modes", modeList);
        return tag;
    }

    private static ListTag writeBoards(Map<String, List<Row>> board) {
        ListTag out = new ListTag();
        board.forEach((key, rows) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", key);
            ListTag rowList = new ListTag();
            for (Row r : rows) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Id", r.player());
                t.putString("Name", r.name());
                t.putInt("Score", r.score());
                t.putInt("Seconds", r.seconds());
                t.putInt("Party", r.party());
                t.putLong("When", r.when());
                rowList.add(t);
            }
            entry.put("Rows", rowList);
            out.add(entry);
        });
        return out;
    }

    private static void readBoards(ListTag list, Map<String, List<Row>> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            List<Row> rows = new ArrayList<>();
            ListTag rowList = entry.getList("Rows", Tag.TAG_COMPOUND);
            for (int j = 0; j < rowList.size(); j++) {
                CompoundTag t = rowList.getCompound(j);
                if (!t.hasUUID("Id")) {
                    continue;
                }
                rows.add(new Row(t.getUUID("Id"), t.getString("Name"), t.getInt("Score"),
                        t.getInt("Seconds"), t.getInt("Party"), t.getLong("When")));
            }
            into.put(entry.getString("Key"), rows);
        }
    }

    public static Leaderboards load(CompoundTag tag, HolderLookup.Provider registries) {
        Leaderboards lb = new Leaderboards();
        readBoards(tag.getList("Solo", Tag.TAG_COMPOUND), lb.solo);
        readBoards(tag.getList("Group", Tag.TAG_COMPOUND), lb.group);
        ListTag modeList = tag.getList("Modes", Tag.TAG_COMPOUND);
        for (int i = 0; i < modeList.size(); i++) {
            CompoundTag one = modeList.getCompound(i);
            try {
                lb.modes.put(one.getString("Key"), Mode.valueOf(one.getString("Mode")));
            } catch (IllegalArgumentException ignored) {
                lb.modes.put(one.getString("Key"), Mode.ROUNDS);
            }
        }
        return lb;
    }

    public static SavedData.Factory<Leaderboards> factory() {
        return new SavedData.Factory<>(Leaderboards::new, Leaderboards::load, null);
    }

    public static Leaderboards get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }
}
