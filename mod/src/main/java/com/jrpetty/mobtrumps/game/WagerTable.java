package com.jrpetty.mobtrumps.game;

/**
 * What two players are playing for, and whether they have both said yes to it.
 *
 * <p>A duel's price used to be the challenger's to set: they named a bet, and
 * the other player's only answers were "match it" or "no". That is not a wager,
 * it is an ultimatum. Here the price sits on the table between them — either
 * side can name a new one, and the game deals only when both have agreed to the
 * number showing.
 *
 * <p>Two rules do all the work.
 *
 * <p><b>Moving the price un-agrees everybody, including the mover.</b> An
 * agreement is to a number, not to the idea of playing, so it cannot survive
 * the number changing. Without this the obvious cheat writes itself: wait for
 * them to agree at ten, then quietly raise it to ten thousand and deal.
 *
 * <p><b>Re-proposing the number already showing changes nothing.</b> It is not
 * a price move, so it cannot be used to flick somebody's agreement off and on,
 * and a player who types what is already there is not punished for it.
 *
 * <p>Nothing is taken from anyone here — this is talk, and either player can
 * walk away from it having paid nothing. Stakes are collected once, at the
 * moment both agreements are in.
 */
public final class WagerTable {

    /** A duel with nothing on it is perfectly legal — that is a friendly game. */
    public static final int MIN_PRICE = 0;

    /**
     * The ceiling on a duel's price.
     *
     * <p>Not a balance decision: the pot is {@code price * 2} and is carried in
     * an int, so the cap keeps every total a long way inside one. It is also
     * more emeralds than a player can reasonably hold, so it never binds in
     * practice.
     */
    public static final int MAX_PRICE = 1_000_000;

    public static final int SEAT_A = 0;
    public static final int SEAT_B = 1;

    /** Returned by {@link #lastMover()} before anyone has moved the price. */
    public static final int NOBODY = -1;

    private int price;
    private final boolean[] agreed = new boolean[2];
    private int lastMover = NOBODY;

    public WagerTable(int openingPrice) {
        this.price = clamp(openingPrice);
    }

    /** A price the table will hold. */
    public static int clamp(int price) {
        return Math.max(MIN_PRICE, Math.min(MAX_PRICE, price));
    }

    public static boolean isSeat(int seat) {
        return seat == SEAT_A || seat == SEAT_B;
    }

    public int price() {
        return price;
    }

    public boolean agreed(int seat) {
        return isSeat(seat) && agreed[seat];
    }

    /** True when the game can deal: both players have agreed the price showing. */
    public boolean settled() {
        return agreed[SEAT_A] && agreed[SEAT_B];
    }

    /** Who last moved the price, or {@link #NOBODY} if it is still the opening ask. */
    public int lastMover() {
        return lastMover;
    }

    /**
     * Name a price. Both agreements come off, because they were to the old
     * number. Returns false — and changes nothing at all — if the price on the
     * table is already the one being asked for.
     */
    public boolean propose(int seat, int value) {
        if (!isSeat(seat)) {
            return false;
        }
        int want = clamp(value);
        if (want == price) {
            return false;
        }
        price = want;
        lastMover = seat;
        agreed[SEAT_A] = false;
        agreed[SEAT_B] = false;
        return true;
    }

    /** Agree to the price showing. Returns false if this seat had already agreed. */
    public boolean agree(int seat) {
        if (!isSeat(seat) || agreed[seat]) {
            return false;
        }
        agreed[seat] = true;
        return true;
    }

    /**
     * Put the price back up for discussion: nobody is agreed any more, but the
     * number stays on the table as the starting point for the next round of it.
     *
     * <p>This is the way back from a price both players have accepted but the
     * game has not yet dealt on. Returns false when there was no agreement to
     * withdraw.
     */
    public boolean reopen() {
        if (!agreed[SEAT_A] && !agreed[SEAT_B]) {
            return false;
        }
        agreed[SEAT_A] = false;
        agreed[SEAT_B] = false;
        return true;
    }

    /** The other seat. */
    public static int otherSeat(int seat) {
        return seat == SEAT_A ? SEAT_B : SEAT_A;
    }
}
