package com.gadgets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which passcodes each player has proven to the server this session.
 *
 * <p>The tablet's screens are decoration; this table is the lock. A report is
 * only ever sent to a player whose held tablet is either unlocked or answered
 * by a hash recorded here, so a client that skips the passcode screen skips
 * nothing — the server simply never answers it.
 *
 * <p>Proof is of a passcode, not a tablet: anyone told the four digits can open
 * any tablet locked with them, which is the whole arrangement — a passcode
 * shared is access shared. Nothing is saved; a restart asks everyone again.
 */
public final class TabletAuth {

    private static final Map<UUID, Set<String>> PROVEN = new HashMap<>();

    private TabletAuth() {
    }

    public static synchronized void grant(UUID player, String hash) {
        if (hash != null && !hash.isEmpty()) {
            PROVEN.computeIfAbsent(player, k -> new HashSet<>()).add(hash);
        }
    }

    public static synchronized boolean proven(UUID player, String hash) {
        Set<String> hashes = PROVEN.get(player);
        return hashes != null && hashes.contains(hash);
    }

    /** Dropped on server stop, so nothing leaks into the next world. */
    public static synchronized void clear() {
        PROVEN.clear();
    }
}
