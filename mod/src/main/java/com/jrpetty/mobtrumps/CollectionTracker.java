package com.jrpetty.mobtrumps;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Server-side bookkeeping of which cards each player has collected. */
public final class CollectionTracker {

    private CollectionTracker() {
    }

    /** Mark a card as collected. Returns true if it is newly discovered. */
    public static boolean record(ServerPlayer player, String cardId) {
        List<String> current = player.getData(ModAttachments.COLLECTED.get());
        if (current.contains(cardId)) {
            return false;
        }
        List<String> next = new ArrayList<>(current);
        next.add(cardId);
        player.setData(ModAttachments.COLLECTED.get(), List.copyOf(next));
        sync(player);
        return true;
    }

    /** Push the player's collection to their client for the book screen. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new CollectionSyncPayload(player.getData(ModAttachments.COLLECTED.get())));
    }
}
