package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DuelWagerPayload;

/** The wager table's last known state, as this client sees it. */
public final class ClientDuelWager {

    private static volatile DuelWagerPayload state = DuelWagerPayload.closed();

    private ClientDuelWager() {
    }

    public static void set(DuelWagerPayload payload) {
        state = payload;
    }

    public static boolean open() {
        return state.phase() == DuelWagerPayload.OPEN;
    }

    public static String opponent() {
        return state.opponent();
    }

    public static int price() {
        return state.num(DuelWagerPayload.PRICE, 0);
    }

    public static boolean youAgreed() {
        return state.num(DuelWagerPayload.YOU_AGREED, 0) == 1;
    }

    public static boolean theyAgreed() {
        return state.num(DuelWagerPayload.THEY_AGREED, 0) == 1;
    }

    public static int purse() {
        return state.num(DuelWagerPayload.YOUR_PURSE, 0);
    }

    public static int seconds() {
        return state.num(DuelWagerPayload.SECONDS, 0);
    }

    public static int bestOf() {
        return state.num(DuelWagerPayload.BEST_OF, 1);
    }

    public static boolean cardWager() {
        return state.num(DuelWagerPayload.CARD_WAGER, 0) == 1;
    }

    /** Who last moved the price: MOVER_NOBODY, MOVER_YOU or MOVER_THEM. */
    public static int lastMover() {
        return state.num(DuelWagerPayload.LAST_MOVER, DuelWagerPayload.MOVER_NOBODY);
    }

    public static boolean theyCanAfford() {
        return state.num(DuelWagerPayload.THEY_CAN_AFFORD, 1) == 1;
    }

    public static boolean youCanAfford() {
        return purse() >= price();
    }

    public static void clear() {
        state = DuelWagerPayload.closed();
    }
}
