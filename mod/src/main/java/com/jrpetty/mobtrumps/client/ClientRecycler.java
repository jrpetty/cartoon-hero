package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.RecyclerResultPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;

/**
 * Client-side recycler state: how many fragments you hold, and the outcome of
 * the run the machine is currently playing out.
 */
public final class ClientRecycler {

    private static volatile int fragments;

    private static volatile int kind = -1;
    private static volatile MobCard card;
    private static volatile int amount;
    private static volatile int count;
    private static volatile long at;

    private ClientRecycler() {
    }

    public static void set(int n) {
        fragments = Math.max(0, n);
    }

    public static int fragments() {
        return fragments;
    }

    /** A machine finished. The screen reads this to play the run out. */
    public static void result(RecyclerResultPayload payload) {
        kind = payload.kind();
        card = payload.mobId().isEmpty() ? null : MobCards.byId(payload.mobId());
        amount = payload.amount();
        count = payload.count();
        at = System.currentTimeMillis();
    }

    public static void clear() {
        kind = -1;
        at = 0L;
    }

    public static int kind() { return kind; }
    public static MobCard card() { return card; }
    public static int amount() { return amount; }
    public static int count() { return count; }
    public static long at() { return at; }

    /** True once the server has answered the run the screen is playing. */
    public static boolean resolvedSince(long started) {
        return at >= started && kind >= 0;
    }
}
