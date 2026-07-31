package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The printing press rests on one economic promise: <em>staking more never
 * changes what a card costs you on average.</em> Thirty fragments into a common
 * is a guaranteed card; fifteen is a coin flip for half the price. Neither is
 * the smart play, so the choice is about appetite for risk rather than about
 * arithmetic, and nobody needs a spreadsheet to gamble well.
 *
 * <p>That promise is one division away from being broken — a consolation
 * refund, a floor on the chance, a rounding step in the wrong place — and it
 * would break quietly, since the UI would still look correct. So it is
 * asserted at every legal stake of every tier rather than spot-checked.
 */
class RecyclerTest {

    @Test
    void expectedCostIsFlatInTheStake() {
        for (Tier tier : Tier.values()) {
            float baseline = Recycler.expectedCost(tier, Recycler.maxStake(tier));
            for (int stake = Recycler.MIN_STAKE; stake <= Recycler.maxStake(tier); stake++) {
                float cost = Recycler.expectedCost(tier, stake);
                assertEquals(baseline, cost, 0.001f,
                        tier + " at stake " + stake + " costs " + cost
                                + " per card but " + baseline + " at full stake");
            }
        }
    }

    @Test
    void aFullStakeIsAGuaranteeAndNothingExceedsIt() {
        for (Tier tier : Tier.values()) {
            assertEquals(1f, Recycler.chance(tier, Recycler.maxStake(tier)), 0.0001f);
            assertEquals(1f, Recycler.chance(tier, Recycler.maxStake(tier) * 3), 0.0001f);
        }
    }

    @Test
    void chanceIsAProbabilityAndRisesWithTheStake() {
        for (Tier tier : Tier.values()) {
            float previous = -1f;
            for (int stake = 0; stake <= Recycler.maxStake(tier) + 20; stake++) {
                float p = Recycler.chance(tier, stake);
                assertTrue(p >= 0f && p <= 1f, tier + " stake " + stake + " -> " + p);
                assertTrue(p >= previous, tier + " chance fell at stake " + stake);
                previous = p;
                int pct = Recycler.percent(tier, stake);
                assertTrue(pct >= 0 && pct <= 100, tier + " percent " + pct);
            }
        }
    }

    @Test
    void clampAlwaysProducesALegalStake() {
        for (Tier tier : Tier.values()) {
            for (int stake = -100; stake <= Recycler.maxStake(tier) + 100; stake++) {
                int s = Recycler.clampStake(tier, stake);
                assertTrue(s >= Recycler.MIN_STAKE && s <= Recycler.maxStake(tier),
                        tier + " clamped " + stake + " to an illegal " + s);
            }
        }
    }

    @Test
    void shreddingPaysMoreForABetterCardAndNeverNothing() {
        for (Tier tier : Tier.values()) {
            int previous = 0;
            for (int condition = 0; condition <= 100; condition++) {
                int paid = Recycler.yield(tier, condition);
                assertTrue(paid >= 1, tier + " at condition " + condition + " paid " + paid);
                assertTrue(paid >= previous,
                        tier + " paid less for condition " + condition + " than for " + (condition - 1));
                previous = paid;
            }
            assertEquals(Recycler.baseYield(tier), Recycler.yield(tier, 100),
                    tier + " mint card should pay the full base yield");
        }
    }

    @Test
    void shreddingIsNeverAWayToPrintFragmentsForever() {
        // A card must never be worth more shredded than it costs to print, or
        // the loop pays for itself and fragments become infinite.
        for (Tier tier : Tier.values()) {
            float cheapestPrint = Recycler.expectedCost(tier, Recycler.maxStake(tier));
            int bestShred = Recycler.yield(tier, 100);
            assertTrue(bestShred < cheapestPrint,
                    tier + ": shredding pays " + bestShred + " but printing costs "
                            + cheapestPrint + " — that is a fragment loop");
        }
    }

    @Test
    void rarerCardsCostMoreToPrintThanCommonOnes() {
        Tier[] ladder = {Tier.COMMON, Tier.UNCOMMON, Tier.RARE, Tier.EPIC, Tier.LEGENDARY};
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(Recycler.maxStake(ladder[i]) > Recycler.maxStake(ladder[i - 1]),
                    ladder[i] + " is not dearer than " + ladder[i - 1]);
            assertTrue(Recycler.baseYield(ladder[i]) > Recycler.baseYield(ladder[i - 1]),
                    ladder[i] + " does not shred for more than " + ladder[i - 1]);
        }
    }
}
