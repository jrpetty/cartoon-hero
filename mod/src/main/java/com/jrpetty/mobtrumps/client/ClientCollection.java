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
    private static volatile List<String> deck = List.of();
    private static volatile Set<String> displayFoil = Set.of();

    private ClientCollection() {
    }

    public static void set(List<String> ids, List<String> foilIds, int wins, List<String> deckIds,
                           List<String> displayFoilIds) {
        collected = Set.copyOf(ids);
        foils = Set.copyOf(foilIds);
        duelWins = wins;
        deck = List.copyOf(deckIds);
        displayFoil = Set.copyOf(displayFoilIds);
    }

    /** Whether the chosen top-of-pile variant for this mob is its foil. */
    public static boolean displayedIsFoil(String cardId) {
        return displayFoil.contains(cardId) && foils.contains(cardId);
    }

    /** How many distinct variants of a mob the player owns (normal + foil). */
    public static int variantCount(String cardId) {
        int n = collected.contains(cardId) ? 1 : 0;
        if (foils.contains(cardId)) n++;
        return n;
    }

    public static List<String> deck() {
        return deck;
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
