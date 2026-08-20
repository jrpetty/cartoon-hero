package com.gadgets;

import java.util.HashSet;
import java.util.Set;

/**
 * The client's side of the tablet lock: which hashes this session has already
 * proven, and how the request in flight is faring.
 *
 * <p>Free of client-only types for the same reason {@link ClientHubReport} is —
 * the payload handler that fills it is named from common code. This state is
 * convenience, not security: it decides which screen to show, while the server
 * decides what gets answered.
 */
public final class ClientTabletLock {
    public static final int IDLE = 0;
    public static final int WAITING = 1;
    public static final int DENIED = 2;
    public static final int GRANTED = 3;

    private static int state = IDLE;
    /** Every hash proven this session, so a tablet already opened stays open. */
    private static final Set<String> proven = new HashSet<>();

    private ClientTabletLock() {
    }

    public static void asked() {
        state = WAITING;
    }

    public static void reset() {
        state = IDLE;
    }

    public static void accept(TabletLockPayload.Result result) {
        if (result.ok()) {
            state = GRANTED;
            proven.add(result.hash());
        } else {
            state = DENIED;
        }
    }

    public static int state() {
        return state;
    }

    public static boolean proven(String hash) {
        return !hash.isEmpty() && proven.contains(hash);
    }

    /** True exactly once per grant — the screen that consumes it moves on. */
    public static boolean consumeGranted() {
        if (state == GRANTED) {
            state = IDLE;
            return true;
        }
        return false;
    }
}
