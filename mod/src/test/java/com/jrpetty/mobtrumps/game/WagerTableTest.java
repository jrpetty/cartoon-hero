package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WagerTableTest {

    @Test
    void opensAtTheAskingPrice() {
        assertEquals(250, new WagerTable(250).price());
        assertEquals(WagerTable.MIN_PRICE, new WagerTable(-5).price());
        assertEquals(WagerTable.MAX_PRICE, new WagerTable(Integer.MAX_VALUE).price());
    }

    @Test
    void nobodyIsAgreedToStartWith() {
        WagerTable t = new WagerTable(100);
        assertFalse(t.agreed(WagerTable.SEAT_A));
        assertFalse(t.agreed(WagerTable.SEAT_B));
        assertFalse(t.settled());
        assertEquals(WagerTable.NOBODY, t.lastMover());
    }

    @Test
    void bothAgreeingSettlesTheTable() {
        WagerTable t = new WagerTable(100);
        assertTrue(t.agree(WagerTable.SEAT_A));
        assertFalse(t.settled(), "one agreement is not a deal");
        assertTrue(t.agree(WagerTable.SEAT_B));
        assertTrue(t.settled());
    }

    @Test
    void agreeingTwiceChangesNothing() {
        WagerTable t = new WagerTable(100);
        assertTrue(t.agree(WagerTable.SEAT_A));
        assertFalse(t.agree(WagerTable.SEAT_A));
        assertFalse(t.settled());
    }

    /**
     * The cheat this rule exists to stop: let them agree cheap, then raise the
     * price without their say-so and deal on the new number.
     */
    @Test
    void movingThePriceUnAgreesEverybody() {
        WagerTable t = new WagerTable(10);
        t.agree(WagerTable.SEAT_A);
        t.agree(WagerTable.SEAT_B);
        assertTrue(t.settled());

        assertTrue(t.propose(WagerTable.SEAT_A, 10_000));
        assertEquals(10_000, t.price());
        assertFalse(t.settled(), "a raise must not carry the old agreement with it");
        assertFalse(t.agreed(WagerTable.SEAT_A), "not even the mover stays agreed");
        assertFalse(t.agreed(WagerTable.SEAT_B));
    }

    @Test
    void reProposingTheSamePriceIsNotAPriceMove() {
        WagerTable t = new WagerTable(500);
        t.agree(WagerTable.SEAT_B);
        assertFalse(t.propose(WagerTable.SEAT_A, 500));
        assertTrue(t.agreed(WagerTable.SEAT_B), "typing what is already there must not un-agree anyone");
        assertEquals(WagerTable.NOBODY, t.lastMover());
    }

    @Test
    void proposalsAreClampedNotRejected() {
        WagerTable t = new WagerTable(0);
        t.propose(WagerTable.SEAT_A, Integer.MIN_VALUE);
        assertEquals(WagerTable.MIN_PRICE, t.price());
        t.propose(WagerTable.SEAT_B, Integer.MAX_VALUE);
        assertEquals(WagerTable.MAX_PRICE, t.price());
    }

    @Test
    void reopenWithdrawsBothAgreementsButKeepsThePrice() {
        WagerTable t = new WagerTable(750);
        t.agree(WagerTable.SEAT_A);
        t.agree(WagerTable.SEAT_B);
        assertTrue(t.reopen());
        assertFalse(t.settled());
        assertEquals(750, t.price(), "reopening is not a reset to zero — the number stays to argue over");
    }

    @Test
    void reopenDoesNothingWhenNobodyHasAgreed() {
        WagerTable t = new WagerTable(750);
        assertFalse(t.reopen());
    }

    @Test
    void theLastMoverIsRemembered() {
        WagerTable t = new WagerTable(10);
        t.propose(WagerTable.SEAT_B, 40);
        assertEquals(WagerTable.SEAT_B, t.lastMover());
        t.propose(WagerTable.SEAT_A, 90);
        assertEquals(WagerTable.SEAT_A, t.lastMover());
    }

    @Test
    void badSeatsAreIgnoredEntirely() {
        WagerTable t = new WagerTable(10);
        assertFalse(t.propose(7, 999));
        assertFalse(t.agree(-1));
        assertEquals(10, t.price());
        assertFalse(t.settled());
    }

    @Test
    void aFriendlyGameIsAPriceOfZero() {
        WagerTable t = new WagerTable(0);
        assertEquals(0, t.price());
        t.agree(WagerTable.SEAT_A);
        t.agree(WagerTable.SEAT_B);
        assertTrue(t.settled(), "playing for nothing still has to be agreed by both");
    }

    /**
     * The property that makes the negotiation safe to walk away from: however
     * the two of them shove the number around, the table can only ever settle
     * on a price BOTH of them agreed to after it last moved.
     */
    @Test
    void aSettledTableAlwaysHasBothAgreementsOnTheCurrentPrice() {
        Random rng = new Random(20260809L);
        for (int game = 0; game < 20_000; game++) {
            WagerTable t = new WagerTable(rng.nextInt(2000));
            int agreedPrice = -1;
            for (int move = 0; move < 40; move++) {
                int seat = rng.nextInt(2);
                switch (rng.nextInt(4)) {
                    case 0 -> t.propose(seat, rng.nextInt(3000) - 500);
                    case 1 -> t.agree(seat);
                    case 2 -> t.reopen();
                    default -> t.agree(WagerTable.otherSeat(seat));
                }
                if (t.settled()) {
                    assertTrue(t.agreed(WagerTable.SEAT_A));
                    assertTrue(t.agreed(WagerTable.SEAT_B));
                    agreedPrice = t.price();
                }
                // whatever happens next, a settled table cannot silently become
                // a settled table at a DIFFERENT price
                if (agreedPrice >= 0 && t.settled()) {
                    assertEquals(agreedPrice, t.price(),
                            "the price changed while both players were still marked agreed");
                }
                if (!t.settled()) {
                    agreedPrice = -1;
                }
                assertTrue(t.price() >= WagerTable.MIN_PRICE && t.price() <= WagerTable.MAX_PRICE);
            }
        }
    }
}
