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

    /**
     * The player's deck as cards, dropping any they no longer own. Cards whose
     * holographic the player has unlocked are played in their boosted form.
     */
    public static List<MobCard> deckCards(ServerPlayer player) {
        Set<String> collected = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        Set<String> foils = new LinkedHashSet<>(player.getData(ModAttachments.COLLECTED_FOIL.get()));
        List<MobCard> cards = new ArrayList<>();
        for (String id : player.getData(ModAttachments.DECK.get())) {
            if (collected.contains(id)) {
                MobCard card = MobCards.byId(id);
                if (card != null) cards.add(foils.contains(id) ? card.foilVersion() : card);
            }
        }
        return cards;
    }
}
