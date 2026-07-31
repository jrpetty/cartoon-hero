package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.advancement.ModTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Server-side bookkeeping of which cards each player has collected. */
public final class CollectionTracker {

    private CollectionTracker() {
    }

    /** Mark a card as collected. Returns true if the base card is newly discovered. */
    public static boolean record(ServerPlayer player, String cardId) {
        return record(player, cardId, false);
    }

    /**
     * Mark a card (and, if {@code foil}, its foil variant) as collected.
     * Returns true if the base card is newly discovered.
     */
    public static boolean record(ServerPlayer player, String cardId, boolean foil) {
        boolean isNew = addTo(player, ModAttachments.COLLECTED.get(), cardId);
        boolean changed = isNew;
        if (foil) {
            boolean newFoil = addTo(player, ModAttachments.COLLECTED_FOIL.get(), cardId);
            changed |= newFoil;
            // a freshly pulled foil becomes the pile-top by default
            if (newFoil) {
                changed |= addTo(player, ModAttachments.DISPLAY_FOIL.get(), cardId);
            }
        }
        if (isNew) {
            // a newly-collected mob may have completed its category
            CategoryRewards.checkAndAward(player);
            if (player.getServer() != null) {
                HallOfFame.get(player.getServer()).checkCompletion(player);
            }
        }
        if (changed) {
            sync(player);
            AchievementManager.refresh(player);
            ModTriggers.COLLECTION.get().trigger(player,
                    player.getData(ModAttachments.COLLECTED.get()).size(),
                    player.getData(ModAttachments.COLLECTED_FOIL.get()).size());
        }
        return isNew;
    }

    private static boolean addTo(ServerPlayer player,
                                 net.neoforged.neoforge.attachment.AttachmentType<List<String>> attachment,
                                 String cardId) {
        List<String> current = player.getData(attachment);
        if (current.contains(cardId)) {
            return false;
        }
        List<String> next = new ArrayList<>(current);
        next.add(cardId);
        player.setData(attachment, next); // already a fresh list; copyOf would copy it twice
        return true;
    }

    /**
     * Give a mob up, because its card has just left the book.
     *
     * <p>The Collection Book is the record of what you own, not a list of
     * everything you have ever seen. Taking a card out to sell or trade it used
     * to leave the entry sitting there ticked, so a player could empty the
     * whole binder and still show 81/81 and play every card. After a
     * withdrawal the mob counts as collected only while a copy — plain or foil
     * — is still filed, and the foil variant only while the foil itself is.
     *
     * <p>Awards already claimed stay claimed; this only moves progress.
     */
    public static void releaseToBinder(ServerPlayer player, String cardId) {
        boolean hasPlain = player.getData(ModAttachments.STORED.get()).contains(cardId);
        boolean hasFoil = player.getData(ModAttachments.STORED_FOIL.get()).contains(cardId);
        boolean changed = false;
        if (!hasPlain && !hasFoil) {
            changed = removeFrom(player, ModAttachments.COLLECTED.get(), cardId);
        }
        if (!hasFoil) {
            changed |= removeFrom(player, ModAttachments.COLLECTED_FOIL.get(), cardId);
            // the pile-top can't be a foil you no longer have
            changed |= removeFrom(player, ModAttachments.DISPLAY_FOIL.get(), cardId);
        }
        if (changed) {
            sync(player);
            AchievementManager.refresh(player);
        }
    }

    private static boolean removeFrom(ServerPlayer player,
                                      net.neoforged.neoforge.attachment.AttachmentType<List<String>> attachment,
                                      String cardId) {
        List<String> current = player.getData(attachment);
        if (!current.contains(cardId)) {
            return false;
        }
        List<String> next = new ArrayList<>(current);
        next.remove(cardId);
        player.setData(attachment, List.copyOf(next));
        return true;
    }

    /** Re-evaluate all Mob Trumps advancement triggers for a player (on login). */
    public static void revalidate(ServerPlayer player) {
        ModTriggers.COLLECTION.get().trigger(player,
                player.getData(ModAttachments.COLLECTED.get()).size(),
                player.getData(ModAttachments.COLLECTED_FOIL.get()).size());
        ModTriggers.DUEL_WIN.get().trigger(player, player.getData(ModAttachments.DUEL_WINS.get()));
        AchievementManager.refresh(player);
    }

    public static void addDuelWin(ServerPlayer player) {
        int wins = player.getData(ModAttachments.DUEL_WINS.get()) + 1;
        player.setData(ModAttachments.DUEL_WINS.get(), wins);
        ModTriggers.DUEL_WIN.get().trigger(player, wins);
        sync(player);
        AchievementManager.refresh(player);
    }

    /** Choose which variant of a mob shows on top of the pile in the book. */
    public static void setDisplayFoil(ServerPlayer player, String cardId, boolean foil) {
        boolean owns = player.getData(ModAttachments.COLLECTED.get()).contains(cardId);
        boolean ownsFoil = player.getData(ModAttachments.COLLECTED_FOIL.get()).contains(cardId);
        if (!owns || (foil && !ownsFoil)) {
            return; // can't display something you don't own
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(player.getData(ModAttachments.DISPLAY_FOIL.get()));
        if (foil) {
            set.add(cardId);
        } else {
            set.remove(cardId);
        }
        player.setData(ModAttachments.DISPLAY_FOIL.get(), List.copyOf(set));
        sync(player);
    }

    /**
     * Ask for the collection to be pushed. Coalesced by {@link ServerSync} —
     * a burst of kills produces one packet, not one per kill.
     */
    public static void sync(ServerPlayer player) {
        ServerSync.markCollection(player);
    }

    /** Actually build and send the packet. Called by the flush, or on login. */
    public static void sendNow(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CollectionSyncPayload(
                player.getData(ModAttachments.COLLECTED.get()),
                player.getData(ModAttachments.COLLECTED_FOIL.get()),
                player.getData(ModAttachments.DUEL_WINS.get()),
                player.getData(ModAttachments.DECK.get()),
                player.getData(ModAttachments.DISPLAY_FOIL.get()),
                player.getData(ModAttachments.KILLS.get())));
    }
}
