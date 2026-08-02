package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wager ladder. These guard the two things that would actually hurt: a
 * payout that wraps around into nonsense, and a curve that quietly hands the
 * player free money at an unbounded stake.
 */
class GuessWhoWagerTest {

    @Test
    void fasterAlwaysPaysBetter() {
        for (int q = 1; q < 14; q++) {
            assertTrue(GuessWhoWager.percentFor(q) >= GuessWhoWager.percentFor(q + 1),
                    "rung " + q + " must not pay less than rung " + (q + 1));
        }
    }

    /**
     * There is deliberately no break-even rung: six pays and seven costs, so
     * every game settles. If a 100 ever appears the ladder has been flattened.
     */
    @Test
    void noRungIsExactlyBreakEven() {
        for (int q = 1; q < 20; q++) {
            assertFalse(GuessWhoWager.percentFor(q) == 100,
                    "rung " + q + " is a dead break-even");
        }
        assertTrue(GuessWhoWager.isProfit(6), "six questions should pay");
        assertFalse(GuessWhoWager.isProfit(7), "seven questions should cost");
    }

    /**
     * The load-bearing one. Eighty-one faces separate in six or seven questions
     * almost every game, so the ladder has to be a loser over that band or an
     * unbounded stake becomes free money. Distribution measured by simulating
     * the real question catalogue; see {@link GuessWhoWager}.
     */
    @Test
    void theLadderDoesNotPayOutOverTheBandTheGameActuallyLivesIn() {
        double strong = 0.58 * GuessWhoWager.percentFor(6) + 0.42 * GuessWhoWager.percentFor(7);
        assertTrue(strong <= 100.5,
                "near-perfect play returns " + strong / 100 + "x — the house cannot win");

        double[][] casual = {{4, 0.004}, {5, 0.05}, {6, 0.46}, {7, 0.41}, {8, 0.07}, {9, 0.006}};
        double loose = 0;
        for (double[] row : casual) {
            loose += row[1] * GuessWhoWager.percentFor((int) row[0]);
        }
        assertTrue(loose < 100, "careless play returns " + loose / 100 + "x");
        assertTrue(loose > 90, "careless play returns " + loose / 100 + "x — too punishing");
    }

    @Test
    void payoutNeverWrapsAround() {
        for (int q = 1; q <= 12; q++) {
            for (int stake : new int[]{GuessWhoWager.MIN_STAKE, 1000, 1_000_000,
                    GuessWhoWager.MAX_STAKE, Integer.MAX_VALUE}) {
                int paid = GuessWhoWager.payout(stake, q);
                assertTrue(paid >= 0, "negative payout: stake " + stake + " rung " + q);
                assertTrue(paid <= (long) GuessWhoWager.MAX_STAKE
                                * GuessWhoWager.BEST_PERCENT / 100,
                        "payout above the ladder's own ceiling");
            }
        }
    }

    @Test
    void stakesAreClampedToTheTable() {
        assertEquals(GuessWhoWager.MIN_STAKE, GuessWhoWager.clampStake(0));
        assertEquals(GuessWhoWager.MIN_STAKE, GuessWhoWager.clampStake(-500));
        assertEquals(GuessWhoWager.MIN_STAKE, GuessWhoWager.clampStake(9));
        assertEquals(10_000, GuessWhoWager.clampStake(10_000));
        assertEquals(GuessWhoWager.MAX_STAKE, GuessWhoWager.clampStake(Integer.MAX_VALUE));
    }

    @Test
    void profitTracksThePayout() {
        for (int q = 4; q <= 10; q++) {
            int stake = 1000;
            assertEquals(GuessWhoWager.payout(stake, q) - stake, GuessWhoWager.profit(stake, q));
            assertEquals(GuessWhoWager.isProfit(q), GuessWhoWager.profit(stake, q) > 0);
        }
    }
}
