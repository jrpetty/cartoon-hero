package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.RankedSyncPayload;

/** The ranked standings as this client last saw them. */
public final class ClientRanked {

    private static volatile RankedSyncPayload state =
            new RankedSyncPayload(java.util.List.of(), java.util.List.of());
    /** When it landed, so the season clock can run on between snapshots. */
    private static volatile long receivedAt;

    private ClientRanked() {
    }

    public static void set(RankedSyncPayload payload) {
        state = payload;
        receivedAt = System.currentTimeMillis();
    }

    public static RankedSyncPayload state() {
        return state;
    }

    public static int count() {
        return state.count();
    }

    public static int season() {
        return state.header(RankedSyncPayload.SEASON, 1);
    }

    /** Seconds left in the season, ticking down between snapshots. */
    public static int secondsLeft() {
        long elapsed = (System.currentTimeMillis() - receivedAt) / 1000L;
        return (int) Math.max(0L, state.header(RankedSyncPayload.SECONDS_LEFT, 0) - elapsed);
    }

    public static int youIndex() {
        return state.header(RankedSyncPayload.YOU_INDEX, -1);
    }

    public static int yourPlace() {
        return state.header(RankedSyncPayload.YOUR_PLACE, -1);
    }

    public static int totalRanked() {
        return state.header(RankedSyncPayload.TOTAL_RANKED, 0);
    }

    public static int decayFloor() {
        return state.header(RankedSyncPayload.DECAY_FLOOR, 0);
    }
}
