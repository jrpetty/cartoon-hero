package com.jrpetty.mobtrumps.client;

import java.util.List;
import java.util.Set;

/**
 * Client-side cache of the player's collection, synced from the server.
 * Pure data holder — safe to load anywhere.
 */
public final class ClientCollection {

    private static volatile Set<String> collected = Set.of();

    private ClientCollection() {
    }

    public static void set(List<String> ids) {
        collected = Set.copyOf(ids);
    }

    public static boolean has(String cardId) {
        return collected.contains(cardId);
    }

    public static int count() {
        return collected.size();
    }
}
