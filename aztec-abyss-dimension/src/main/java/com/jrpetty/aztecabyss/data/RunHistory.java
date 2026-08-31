package com.jrpetty.aztecabyss.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happened, run by run, for each player.
 *
 * <p>The leaderboards answer "who is best", which is a question about other
 * people. This answers "how did I do", which is the one a player actually asks
 * when they come out - and until now the game had no memory of it at all. A run
 * ended, a line went past in chat, and the only trace left was whether you
 * happened to make the top ten of a board.
 *
 * <p>That is the wrong shape for this mod in particular. Almost nobody tops a
 * board; nearly every run is a defeat, and the interesting thing about a defeat
 * is the detail - which day it got you, how much of the map you had charted when
 * it did, whether you were the one who turned. A record that only keeps winners
 * throws away the part of the game people talk about.
 *
 * <p>Kept per player and capped at {@link #KEEP}, newest first. It is a diary,
 * not an archive: the last twenty runs is what somebody will actually read, and
 * an unbounded list is a save file that grows forever for no one's benefit.
 */
public final class RunHistory extends SavedData {

    public static final String NAME = "aztecabyss_run_history";

    /** How many runs each player keeps. */
    private static final int KEEP = 20;

    /** How a run ended. */
    public static final String ESCAPED = "escaped";
    public static final String TAKEN = "taken";
    public static final String CHANGED = "changed";
    public static final String EXTRACTED = "extracted";
    public static final String FELL = "fell";

    /**
     * One run.
     *
     * @param mapKey   which map, matching the leaderboard's keys
     * @param outcome  one of the constants above
     * @param score    day reached in the maze, round reached in an arena
     * @param seconds  how long it lasted
     * @param charted  the maze's percentage charted; headshots in an arena
     * @param party    how many went in together
     */
    public record Run(long when, String mapKey, String outcome, int score, int seconds,
                      int kills, int revives, int charted, int party) {
    }

    private final Map<UUID, List<Run>> runs = new LinkedHashMap<>();

    public static SavedData.Factory<RunHistory> factory() {
        return new SavedData.Factory<>(RunHistory::new, RunHistory::load, null);
    }

    public static RunHistory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** Files a finished run. Newest first, oldest dropped past the cap. */
    public void record(UUID player, Run run) {
        List<Run> list = runs.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(0, run);
        while (list.size() > KEEP) {
            list.remove(list.size() - 1);
        }
        setDirty();
    }

    /** This player's runs, newest first. Never null. */
    public List<Run> forPlayer(UUID player) {
        return List.copyOf(runs.getOrDefault(player, List.of()));
    }

    /** How many of this player's kept runs ended in getting out. */
    public int wins(UUID player) {
        int n = 0;
        for (Run r : forPlayer(player)) {
            if (ESCAPED.equals(r.outcome()) || EXTRACTED.equals(r.outcome())) {
                n++;
            }
        }
        return n;
    }

    /** The best score this player has reached on a map, or 0. */
    public int best(UUID player, String mapKey) {
        int best = 0;
        for (Run r : forPlayer(player)) {
            if (r.mapKey().equals(mapKey)) {
                best = Math.max(best, r.score());
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        runs.forEach((id, list) -> {
            ListTag rows = new ListTag();
            for (Run r : list) {
                CompoundTag t = new CompoundTag();
                t.putLong("When", r.when());
                t.putString("Map", r.mapKey());
                t.putString("Out", r.outcome());
                t.putInt("Score", r.score());
                t.putInt("Secs", r.seconds());
                t.putInt("Kills", r.kills());
                t.putInt("Revives", r.revives());
                t.putInt("Chart", r.charted());
                t.putInt("Party", r.party());
                rows.add(t);
            }
            all.put(id.toString(), rows);
        });
        tag.put("Runs", all);
        return tag;
    }

    public static RunHistory load(CompoundTag tag, HolderLookup.Provider registries) {
        RunHistory h = new RunHistory();
        CompoundTag all = tag.getCompound("Runs");
        for (String key : all.getAllKeys()) {
            List<Run> list = new ArrayList<>();
            ListTag rows = all.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag t = rows.getCompound(i);
                list.add(new Run(t.getLong("When"), t.getString("Map"), t.getString("Out"),
                        t.getInt("Score"), t.getInt("Secs"), t.getInt("Kills"),
                        t.getInt("Revives"), t.getInt("Chart"), t.getInt("Party")));
            }
            try {
                h.runs.put(UUID.fromString(key), list);
            } catch (IllegalArgumentException ignored) {
                // A key that is not a UUID is not ours; dropping it is correct.
            }
        }
        return h;
    }
}
