package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DraftSyncPayload;

import java.util.List;

/** The draft on the client: the last state the server sent, and when it arrived. */
public final class ClientDraft {

    private static volatile DraftSyncPayload state;
    private static volatile long receivedAt;

    private ClientDraft() {
    }

    public static void set(DraftSyncPayload payload) {
        state = payload;
        receivedAt = System.currentTimeMillis();
    }

    public static void clear() {
        state = null;
    }

    public static boolean active() {
        return state != null;
    }

    public static boolean yourTurn() {
        DraftSyncPayload s = state;
        return s != null && s.num(DraftSyncPayload.YOUR_TURN) == 1;
    }

    public static int pickNo() {
        DraftSyncPayload s = state;
        return s == null ? 0 : s.num(DraftSyncPayload.PICK_NO);
    }

    public static int picksEach() {
        DraftSyncPayload s = state;
        return s == null ? 0 : s.num(DraftSyncPayload.PICKS_EACH);
    }

    public static String opponent() {
        DraftSyncPayload s = state;
        return s == null || s.texts().isEmpty() ? "" : s.texts().get(DraftSyncPayload.T_OPPONENT);
    }

    public static List<String> pool() {
        DraftSyncPayload s = state;
        return s == null ? List.of() : s.run(0);
    }

    public static List<String> mine() {
        DraftSyncPayload s = state;
        return s == null ? List.of() : s.run(1);
    }

    public static List<String> theirs() {
        DraftSyncPayload s = state;
        return s == null ? List.of() : s.run(2);
    }

    /** The clock on the pick being made, as a fraction left, 1 to 0. */
    public static float clockLeft() {
        DraftSyncPayload s = state;
        if (s == null) {
            return 0f;
        }
        long total = s.num(DraftSyncPayload.SECONDS) * 1000L;
        if (total <= 0) {
            return 0f;
        }
        long left = total - (System.currentTimeMillis() - receivedAt);
        return Math.max(0f, Math.min(1f, left / (float) total));
    }

    public static int secondsLeft() {
        DraftSyncPayload s = state;
        if (s == null) {
            return 0;
        }
        long left = s.num(DraftSyncPayload.SECONDS) * 1000L - (System.currentTimeMillis() - receivedAt);
        return (int) Math.max(0, (left + 999) / 1000);
    }

    public static long receivedAt() {
        return receivedAt;
    }
}
