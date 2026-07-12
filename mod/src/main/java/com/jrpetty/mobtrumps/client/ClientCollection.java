package com.jrpetty.mobtrumps.client;

import java.util.List;
import java.util.Set;

/**
 * Client-side cache of the player's collection, synced from the server.
 * Pure data holder — safe to load anywhere.
 */
public final class ClientCollection {

    private static volatile Set<String> collected = Set.of();
    private static volatile Set<String> foils = Set.of();
    private static volatile int duelWins = 0;

    private ClientCollection() {
    }

    public static void set(List<String> ids, List<String> foilIds, int wins) {
        collected = Set.copyOf(ids);
        foils = Set.copyOf(foilIds);
        duelWins = wins;
    }

    public static int duelWins() {
        return duelWins;
    }

    public static boolean has(String cardId) {
        return collected.contains(cardId);
    }

    public static boolean hasFoil(String cardId) {
        return foils.contains(cardId);
    }

    public static int count() {
        return collected.size();
    }

    public static int foilCount() {
        return foils.size();
    }
}
