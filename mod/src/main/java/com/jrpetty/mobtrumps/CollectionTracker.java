package com.jrpetty.mobtrumps;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
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
            changed |= addTo(player, ModAttachments.COLLECTED_FOIL.get(), cardId);
        }
        if (changed) {
            sync(player);
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
        player.setData(attachment, List.copyOf(next));
        return true;
    }

    public static void addDuelWin(ServerPlayer player) {
        player.setData(ModAttachments.DUEL_WINS.get(),
                player.getData(ModAttachments.DUEL_WINS.get()) + 1);
    }

    /** Push the player's collection to their client for the book screen. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CollectionSyncPayload(
                player.getData(ModAttachments.COLLECTED.get()),
                player.getData(ModAttachments.COLLECTED_FOIL.get())));
    }
}
