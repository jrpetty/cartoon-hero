package com.jrpetty.mobtrumps.game;

/**
 * What a finished game pays in experience, as plain arithmetic so the rule can
 * be tested without a server.
 *
 * <p>Every game pays the same — a win, a loss and a draw alike — because the
 * point is to make sitting down to play worth the time. A game shorter than
 * {@link #FULL_PAY_MS} pays its share of the full amount instead: nothing a
 * person sits down to play is that short, but a Twenty-One hand can be, and
 * without a floor it would be the quickest experience farm in the game.
 */
public final class GamePay {

    /** A game this long or longer pays the full amount. */
    public static final long FULL_PAY_MS = 15_000L;

    private GamePay() {
    }

    /**
     * Experience for a game that ran {@code durationMs}, given the full rate.
     * Never negative, never more than {@code full}, and at least 1 whenever
     * the rate is switched on — a finished game always pays something.
     */
    public static int xp(int full, long durationMs) {
        if (full <= 0) {
            return 0;
        }
        if (durationMs >= FULL_PAY_MS) {
            return full;
        }
        double share = Math.max(0L, durationMs) / (double) FULL_PAY_MS;
        return Math.max(1, Math.min(full, (int) Math.round(full * share)));
    }
}
