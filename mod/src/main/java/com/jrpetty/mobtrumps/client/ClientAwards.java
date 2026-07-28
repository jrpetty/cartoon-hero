package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.Achievement;
import com.jrpetty.mobtrumps.game.Category;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side cache of the award board (achievement progress, claim state and
 * the set spawn eggs still owed), synced from the server. Pure data holder.
 */
public final class ClientAwards {

    public static final int UNLOCKED = 1;
    public static final int CLAIMED = 2;

    private static volatile Map<String, Integer> progress = Map.of();
    private static volatile Map<String, Integer> states = Map.of();
    private static volatile Set<String> eggPending = Set.of();
    private static volatile Map<String, String> eggClaimed = Map.of();

    private ClientAwards() {
    }

    public static void set(Map<String, Integer> progressMap, Map<String, Integer> stateMap,
                           List<String> pending, List<String> claimed) {
        progress = Map.copyOf(progressMap);
        states = Map.copyOf(stateMap);
        eggPending = Set.copyOf(pending);
        // "FARM:sheep" -> FARM = sheep
        java.util.Map<String, String> byCategory = new java.util.HashMap<>();
        for (String entry : claimed) {
            int split = entry.indexOf(':');
            if (split > 0) {
                byCategory.put(entry.substring(0, split), entry.substring(split + 1));
            }
        }
        eggClaimed = Map.copyOf(byCategory);
    }

    /** How far along an achievement is, capped at its target. */
    public static int progress(Achievement a) {
        return Math.min(a.target(), progress.getOrDefault(a.metric(), 0));
    }

    public static int state(Achievement a) {
        return states.getOrDefault(a.id(), 0);
    }

    public static boolean isClaimed(Achievement a) {
        return state(a) == CLAIMED;
    }

    /** Earned but not yet collected — the state the Collect button lives in. */
    public static boolean isCollectable(Achievement a) {
        return state(a) != CLAIMED && progress(a) >= a.target();
    }

    /** How many awards are sitting there waiting to be collected. */
    public static int collectableCount() {
        int n = 0;
        for (Achievement a : com.jrpetty.mobtrumps.game.Achievements.ALL) {
            if (isCollectable(a)) n++;
        }
        return n + eggPending.size();
    }

    /** This set is complete and still owes a spawn egg. */
    public static boolean eggPending(Category category) {
        return eggPending.contains(category.name());
    }

    /** The mob whose egg was taken for this set, or null if none yet. */
    public static String eggTaken(Category category) {
        return eggClaimed.get(category.name());
    }
}
