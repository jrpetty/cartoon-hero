package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankDecayTest {

    private static final int FLOOR = 1050;   // RankTier.GOLD.min
    private static final long D = RankDecay.DAY_MS;

    @Test
    void theGracePeriodCostsNothing() {
        for (int day = 0; day <= RankDecay.GRACE_DAYS; day++) {
            assertEquals(1600, RankDecay.decayed(1600, day * D, FLOOR),
                    "a player idle " + day + " days inside the grace period lost rating");
        }
    }

    @Test
    void theFirstChargedDayCostsExactlyOneDay() {
        assertEquals(1600 - RankDecay.POINTS_PER_DAY,
                RankDecay.decayed(1600, (RankDecay.GRACE_DAYS + 1) * D, FLOOR));
    }

    @Test
    void nobodyIsEverPushedBelowTheFloor() {
        for (int day = 0; day < 4000; day += 7) {
            assertTrue(RankDecay.decayed(1600, day * D, FLOOR) >= FLOOR,
                    "decay went below the floor after " + day + " days");
        }
        assertEquals(FLOOR, RankDecay.decayed(1600, 100_000L * D, FLOOR),
                "a very long absence should settle exactly on the floor");
    }

    @Test
    void playersAtOrBelowTheFloorAreNeverTouched() {
        for (int rating = 0; rating <= FLOOR; rating += 50) {
            assertEquals(rating, RankDecay.decayed(rating, 9_999L * D, FLOOR),
                    "decay touched a player at or below the floor: " + rating);
            assertFalse(RankDecay.applies(rating, FLOOR));
        }
    }

    @Test
    void moreIdleTimeNeverRaisesARating() {
        int previous = Integer.MAX_VALUE;
        for (int day = 0; day < 600; day++) {
            int now = RankDecay.decayed(1500, day * D, FLOOR);
            assertTrue(now <= previous, "rating went UP after another idle day");
            previous = now;
        }
    }

    @Test
    void absurdInputsDoNotOverflowOrReward() {
        assertEquals(FLOOR, RankDecay.decayed(1600, Long.MAX_VALUE / 2, FLOOR));
        assertEquals(1600, RankDecay.decayed(1600, -5 * D, FLOOR),
                "negative idle time must do nothing at all");
        assertEquals(0, RankDecay.decayDays(-1L));
    }

    /**
     * The property the server actually depends on.
     *
     * <p>Decay runs on a tick, so it is applied over and over rather than once.
     * If each pass charged whole days and then reset the clock to now, the
     * leftover hours would be thrown away every single pass and a player idling
     * for months would decay far slower than the stated rate — the rate would
     * silently become a function of how often the server ticked. Advancing the
     * clock by only the days actually charged is what makes these two equal.
     */
    @Test
    void repeatedApplicationMatchesASingleOne() {
        for (int totalDays : new int[]{6, 10, 30, 90, 365}) {
            int once = RankDecay.decayed(1600, totalDays * D, FLOOR);

            int rating = 1600;
            long last = 0L;
            for (long t = 0; t <= totalDays * D; t += 32_000L) {   // the real cadence
                int next = RankDecay.decayed(rating, t - last, FLOOR);
                if (next != rating) {
                    last += (long) RankDecay.decayDays(t - last) * D;
                    rating = next;
                }
            }
            assertEquals(once, rating,
                    "decay drifted over " + totalDays + " days of repeated application");
        }
    }
}
