package com.jrpetty.aztecabyss.engine;

import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a run remembers.
 *
 * <p>The script layer could do a great many things and could not count. Every
 * rule was a reflex - this happened, do that - with nothing carried between them,
 * so the only state a map could ever have was the round number and which doors
 * were open. That is enough to build a hundred arenas and exactly one game.
 *
 * <p>Almost everything that is not round-survival is counting. Flags captured,
 * keys found, lives left, checkpoints passed, how many of the four generators are
 * running. Give a map one integer it can name and read back, and races, heists,
 * escape rooms and hold-the-point modes stop being impossible and start being
 * configuration.
 *
 * <h2>Two scopes, because they answer different questions</h2>
 *
 * <p>A <b>run</b> variable is the squad's: {@code flags} is how many the team has.
 * A <b>player</b> variable is one person's: {@code keys} is how many <em>you</em>
 * are carrying. Collapsing them into one namespace would force every co-op map to
 * fake per-player state by encoding names into keys, which is exactly the sort of
 * thing that makes a data format hostile.
 *
 * <h2>Deliberately integers only</h2>
 *
 * <p>No strings, no floats, no expressions. An integer with add, set and compare
 * covers counting, flags (0 and 1), timers and scores, and it cannot be used to
 * build something that hangs a server. The script layer's promise is that a map
 * downloaded from a stranger is safe to run, and that promise is worth more than
 * arithmetic.
 */
public final class Vars {

    /** Ceiling on how many distinct names one run may create. */
    private static final int MAX_KEYS = 256;

    private final Map<String, Integer> run = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Integer>> perPlayer = new LinkedHashMap<>();

    private static String key(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Run scope
    // ------------------------------------------------------------------

    public int get(String name) {
        return run.getOrDefault(key(name), 0);
    }

    public void set(String name, int value) {
        String k = key(name);
        if (k.isEmpty() || (run.size() >= MAX_KEYS && !run.containsKey(k))) {
            return;
        }
        run.put(k, value);
    }

    public int add(String name, int delta) {
        int now = get(name) + delta;
        set(name, now);
        return now;
    }

    // ------------------------------------------------------------------
    // Player scope
    // ------------------------------------------------------------------

    public int get(ServerPlayer player, String name) {
        Map<String, Integer> mine = perPlayer.get(player.getUUID());
        return mine == null ? 0 : mine.getOrDefault(key(name), 0);
    }

    public void set(ServerPlayer player, String name, int value) {
        String k = key(name);
        if (k.isEmpty()) {
            return;
        }
        Map<String, Integer> mine = perPlayer.computeIfAbsent(
                player.getUUID(), id -> new LinkedHashMap<>());
        if (mine.size() >= MAX_KEYS && !mine.containsKey(k)) {
            return;
        }
        mine.put(k, value);
    }

    public int add(ServerPlayer player, String name, int delta) {
        int now = get(player, name) + delta;
        set(player, name, now);
        return now;
    }

    /**
     * The sum of one player-scoped name across everybody.
     *
     * <p>Lets a map ask a team question about per-player state without keeping a
     * second copy in sync - "have the four of you collected twelve between you"
     * is one condition rather than four adds and a run variable to hold the total.
     */
    public int total(String name) {
        String k = key(name);
        int sum = 0;
        for (Map<String, Integer> mine : perPlayer.values()) {
            sum += mine.getOrDefault(k, 0);
        }
        return sum;
    }

    public void clear() {
        run.clear();
        perPlayer.clear();
    }

    /** Everything set, for {@code /arena status} and for debugging a map. */
    public Map<String, Integer> snapshot() {
        return Map.copyOf(run);
    }
}
