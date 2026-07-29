package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Server-side storage and validation of each player's custom battle deck. */
public final class DeckManager {

    public static final int MAX_DECK = 16; // client-side fallback cap
    public static final int MIN_DECK = 4;

    private DeckManager() {
    }

    private static int maxDeck() {
        try {
            return Config.DECK_MAX.get();
        } catch (IllegalStateException notLoaded) {
            return MAX_DECK; // config not loaded yet (e.g. client before join)
        }
    }

    /** Save a validated deck: known, collected, distinct mobs, capped at MAX_DECK. */
    public static void saveDeck(ServerPlayer player, List<String> requested) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        List<String> clean = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : requested) {
            String key = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
            if (MobCards.byId(key) != null && collected.contains(key) && seen.add(key)) {
                clean.add(key);
                if (clean.size() >= maxDeck()) break;
            }
        }
        player.setData(ModAttachments.DECK.get(), List.copyOf(clean));
        CollectionTracker.sync(player);
    }

    // --- named deck slots ---

    public static final int MAX_SLOTS = 8;

    /** Save the active deck under a name. Returns false if it failed. */
    public static boolean saveSlot(ServerPlayer player, String name) {
        List<String> deck = player.getData(ModAttachments.DECK.get());
        if (deck.isEmpty()) return false;
        String key = slotKey(name);
        if (key.isEmpty()) return false;
        var slots = new java.util.LinkedHashMap<>(player.getData(ModAttachments.SAVED_DECKS.get()));
        if (!slots.containsKey(key) && slots.size() >= MAX_SLOTS) return false;
        slots.put(key, List.copyOf(deck));
        player.setData(ModAttachments.SAVED_DECKS.get(), java.util.Map.copyOf(slots));
        return true;
    }

    /** Load a named deck into the active slot. Returns false if unknown. */
    public static boolean loadSlot(ServerPlayer player, String name) {
        List<String> saved = player.getData(ModAttachments.SAVED_DECKS.get()).get(slotKey(name));
        if (saved == null) return false;
        saveDeck(player, saved); // re-validates ownership on load
        return true;
    }

    public static boolean deleteSlot(ServerPlayer player, String name) {
        var slots = new java.util.LinkedHashMap<>(player.getData(ModAttachments.SAVED_DECKS.get()));
        if (slots.remove(slotKey(name)) == null) return false;
        player.setData(ModAttachments.SAVED_DECKS.get(), java.util.Map.copyOf(slots));
        return true;
    }

    public static java.util.Map<String, List<String>> slots(ServerPlayer player) {
        return player.getData(ModAttachments.SAVED_DECKS.get());
    }

    private static String slotKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    /**
     * The player's deck as cards, dropping any they no longer own. Cards whose
     * holographic the player has unlocked are played in their boosted form.
     */
    /**
     * The holo upgrade level of each card in the player's deck, in the same
     * order as {@link #deckCards}. The CPU's hand is levelled to match this, so
     * a deck you have hunted hard meets an opponent that has kept up.
     */
    public static List<Integer> deckLevels(ServerPlayer player) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        Set<String> foils = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED_FOIL.get()));
        java.util.Map<String, Integer> kills = player.getData(ModAttachments.KILLS.get());
        List<Integer> levels = new ArrayList<>();
        for (String id : player.getData(ModAttachments.DECK.get())) {
            if (!collected.contains(id)) {
                continue;
            }
            MobCard card = MobCards.byId(id);
            if (card == null) {
                continue;
            }
            int byKills = card.tier().upgradeLevel(kills.getOrDefault(id, 0));
            levels.add(Math.max(foils.contains(id) ? 1 : 0, byKills));
        }
        return levels;
    }

    /** The card ids actually in the player's deck, for dealing the CPU different mobs. */
    public static Set<String> deckIds(ServerPlayer player) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        Set<String> ids = new LinkedHashSet<>();
        for (String id : player.getData(ModAttachments.DECK.get())) {
            if (collected.contains(id)) ids.add(id);
        }
        return ids;
    }

    public static List<MobCard> deckCards(ServerPlayer player) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        Set<String> foils = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED_FOIL.get()));
        java.util.Map<String, Integer> kills = player.getData(ModAttachments.KILLS.get());
        List<MobCard> cards = new ArrayList<>();
        for (String id : player.getData(ModAttachments.DECK.get())) {
            if (collected.contains(id)) {
                MobCard card = MobCards.byId(id);
                // a card whose holo you own plays at your full kill-earned level
                if (card != null) cards.add(card.effective(foils.contains(id), kills.getOrDefault(id, 0)));
            }
        }
        return cards;
    }
}
