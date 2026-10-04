package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side storage and validation of each player's custom battle deck.
 *
 * <p>A deck is exactly {@link #DECK_SIZE} cards — the hand every Top Trumps
 * game is played with — so it can only be fielded once it is full. Decks
 * saved when they could hold sixteen are not cut down on disk: the first
 * {@link #DECK_SIZE} cards that are still owned are the ones that play, and
 * the deck builder opens on exactly those, so the next save makes it so.
 */
public final class DeckManager {

    public static final int DECK_SIZE = Battle.HAND_SIZE;

    private DeckManager() {
    }

    /** Save a validated deck: known, collected, distinct mobs, at most {@link #DECK_SIZE}. */
    public static void saveDeck(ServerPlayer player, List<String> requested) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        List<String> clean = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : requested) {
            String key = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
            if (MobCards.byId(key) != null && collected.contains(key) && seen.add(key)) {
                clean.add(key);
                if (clean.size() >= DECK_SIZE) break;
            }
        }
        player.setData(ModAttachments.DECK.get(), List.copyOf(clean));
        CollectionTracker.sync(player);
    }

    /** True once the deck holds a full hand of cards the player still owns. */
    public static boolean isComplete(ServerPlayer player) {
        return activeIds(player).size() >= DECK_SIZE;
    }

    /**
     * The ids that actually play: the deck's cards the player still owns, in
     * deck order, never more than {@link #DECK_SIZE} of them. Everything that
     * reads the deck goes through here, so the cards, their levels and the
     * ids the CPU avoids always describe the same hand.
     */
    private static List<String> activeIds(ServerPlayer player) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        List<String> ids = new ArrayList<>(DECK_SIZE);
        for (String id : player.getData(ModAttachments.DECK.get())) {
            if (ids.size() >= DECK_SIZE) {
                break;
            }
            if (collected.contains(id) && MobCards.byId(id) != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
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
     * The holo upgrade level of each card in the player's deck, in the same
     * order as {@link #deckCards}. The CPU's hand is levelled to match this, so
     * a deck you have hunted hard meets an opponent that has kept up.
     */
    public static List<Integer> deckLevels(ServerPlayer player) {
        Set<String> foils = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED_FOIL.get()));
        java.util.Map<String, Integer> kills = player.getData(ModAttachments.KILLS.get());
        List<Integer> levels = new ArrayList<>();
        for (String id : activeIds(player)) {
            MobCard card = MobCards.byId(id);
            int byKills = card.tier().upgradeLevel(kills.getOrDefault(id, 0));
            levels.add(Math.max(foils.contains(id) ? 1 : 0, byKills));
        }
        return levels;
    }

    /** The card ids actually in the player's deck, for dealing the CPU different mobs. */
    public static Set<String> deckIds(ServerPlayer player) {
        return new LinkedHashSet<>(activeIds(player));
    }

    /**
     * The player's deck as cards, dropping any they no longer own. Cards whose
     * holographic the player has unlocked are played in their boosted form.
     */
    public static List<MobCard> deckCards(ServerPlayer player) {
        Set<String> foils = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED_FOIL.get()));
        java.util.Map<String, Integer> kills = player.getData(ModAttachments.KILLS.get());
        List<MobCard> cards = new ArrayList<>();
        for (String id : activeIds(player)) {
            // a card whose holo you own plays at your full kill-earned level
            cards.add(MobCards.byId(id).effective(foils.contains(id), kills.getOrDefault(id, 0)));
        }
        return cards;
    }
}
