package com.jrpetty.mobtrumps;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coalesces the mod's outbound state pushes.
 *
 * <p>Collection, award and binder state used to be recomputed and sent the
 * instant anything touched them — which meant every single mob kill rebuilt the
 * whole award board and pushed two packets carrying the player's entire
 * collection. On a mob farm that is hundreds of times a second per player, to
 * redraw a book nobody has open.
 *
 * <p>So callers now mark themselves dirty and the real work happens at most
 * once every {@link #FLUSH_TICKS} ticks. Half a second is invisible for
 * something that only feeds a GUI, and it collapses a burst of kills into a
 * single recompute. Anything the player actually clicked calls
 * {@link #flushNow} instead, so button presses stay instant.
 */
public final class ServerSync {

    /** Twice a second: below human notice, far above the cost of doing it per kill. */
    private static final int FLUSH_TICKS = 10;

    private static final Set<UUID> COLLECTION = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> AWARDS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> STORAGE = ConcurrentHashMap.newKeySet();

    private ServerSync() {
    }

    public static void markCollection(ServerPlayer player) {
        COLLECTION.add(player.getUUID());
    }

    public static void markAwards(ServerPlayer player) {
        AWARDS.add(player.getUUID());
    }

    public static void markStorage(ServerPlayer player) {
        STORAGE.add(player.getUUID());
    }

    /** Push everything this player has pending right now — for clicked actions. */
    public static void flushNow(ServerPlayer player) {
        UUID id = player.getUUID();
        if (COLLECTION.remove(id)) {
            CollectionTracker.sendNow(player);
        }
        if (STORAGE.remove(id)) {
            BinderStorage.sendNow(player);
        }
        if (AWARDS.remove(id)) {
            AchievementManager.recheckNow(player);
        }
    }

    public static void forget(UUID player) {
        COLLECTION.remove(player);
        AWARDS.remove(player);
        STORAGE.remove(player);
    }

    /** Called every server tick; does nothing at all on 9 ticks out of 10. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % FLUSH_TICKS != 0) {
            return;
        }
        if (COLLECTION.isEmpty() && AWARDS.isEmpty() && STORAGE.isEmpty()) {
            return;
        }
        drain(server, COLLECTION, CollectionTracker::sendNow);
        drain(server, STORAGE, BinderStorage::sendNow);
        drain(server, AWARDS, AchievementManager::recheckNow);
    }

    private static void drain(MinecraftServer server, Set<UUID> dirty,
                              java.util.function.Consumer<ServerPlayer> action) {
        if (dirty.isEmpty()) {
            return;
        }
        for (UUID id : dirty.toArray(new UUID[0])) {
            dirty.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        }
    }
}
