package com.jrpetty.mobtrumps.game;

/**
 * What a staked game of Guess Who pays back.
 *
 * <p>You put fragments up before the board is dealt and get a multiple of them
 * back for finding the mob — more the fewer questions it took, less the more.
 * Name the wrong mob and the stake is gone.
 *
 * <p><b>Where the break-even point had to go.</b> Eighty-one faces need
 * log&#8322;(81) &#8776; 6.34 questions to separate, so six or seven is not
 * merely good play, it is close to the floor: simulated over the real question
 * catalogue, a strong player finishes in six 58% of the time and seven the rest,
 * and even a careless one lands in that band about nine times in ten. A neutral
 * point at eight would therefore pay out on a result almost nobody misses —
 * measured at <b>+23% to the player every single game</b>, which at an
 * unbounded stake is not a wager, it is a printing press.
 *
 * <p>So the ladder is centred on the band the game actually lives in, and
 * deliberately has <em>no</em> break-even rung: six pays and seven costs. Every
 * game settles one way or the other rather than handing most players their
 * stake back. Measured against the same simulation, near-perfect play returns
 * 0.999x and careless play 0.982x — the house edge is the mistakes.
 */
public final class GuessWhoWager {

    /** Smallest wager the table will take. */
    public static final int MIN_STAKE = 10;

    /**
     * Largest wager, and it is not a balance decision.
     *
     * <p>A payout is {@code stake * percent / 100} with percent up to
     * {@value #BEST_PERCENT}, so a stake anywhere near {@link Integer#MAX_VALUE}
     * would overflow the arithmetic and pay out a negative fortune. The cap
     * keeps every product inside an int with room to spare; the maths below
     * runs in long regardless, so this is belt and braces.
     */
    public static final int MAX_STAKE = 100_000_000;

    /** The best rung, for the overflow note above. */
    public static final int BEST_PERCENT = 160;

    /**
     * Percentage of the stake returned for finding the mob in {@code questions}
     * questions. 100 would be break even; there is no such rung by design.
     */
    public static int percentFor(int questions) {
        if (questions <= 4) {
            return BEST_PERCENT;
        }
        return switch (questions) {
            case 5 -> 132;
            case 6 -> 110;
            case 7 -> 86;
            case 8 -> 68;
            case 9 -> 50;
            default -> 30;
        };
    }

    /**
     * Fragments handed back for a win. Long arithmetic then clamped, so no
     * combination of stake and rung can wrap around.
     */
    public static int payout(int stake, int questions) {
        long paid = (long) clampStake(stake) * percentFor(questions) / 100L;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, paid));
    }

    /** Profit (or loss) against the stake, for the screen to show. */
    public static int profit(int stake, int questions) {
        return payout(stake, questions) - clampStake(stake);
    }

    /** A wager the table will accept. */
    public static int clampStake(int stake) {
        return Math.max(MIN_STAKE, Math.min(MAX_STAKE, stake));
    }

    /** True when this rung hands back more than went in. */
    public static boolean isProfit(int questions) {
        return percentFor(questions) > 100;
    }

    private GuessWhoWager() {
    }
}
