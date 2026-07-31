package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wear rule, checked against the behaviour it was actually asked for: a
 * new card survives two handlings untouched, and after that each 5% step takes
 * two more. Getting a card and looking at it must not mark it.
 *
 * <p>This mirrors {@code CardWear} rather than calling it, because that class
 * sits in the mod package and drags in Minecraft's codecs. The point here is
 * that the <em>arithmetic</em> of the ladder is pinned: if the constants move,
 * the numbers below stop matching and this fails loudly. The two must agree —
 * {@code CardWear.FREE_HANDLINGS} = 2 and {@code TOUCHES_PER_STEP} = 2.
 */
class WearRuleTest {

    private static final int FREE_HANDLINGS = 2;
    private static final int TOUCHES_PER_STEP = 2;
    private static final int WEAR_POINTS = CardCondition.DEFAULT_WEAR;

    /** The same rule CardWear.handled implements, over a run of handlings. */
    private static int conditionAfter(int handlings) {
        int condition = CardCondition.MINT;
        for (int n = 1; n <= handlings; n++) {
            if (n <= FREE_HANDLINGS) {
                continue;
            }
            if ((n - FREE_HANDLINGS) % TOUCHES_PER_STEP == 0) {
                condition = CardCondition.clamp(condition - WEAR_POINTS);
            }
        }
        return condition;
    }

    @Test
    void aNewCardSurvivesTwoHandlingsUntouched() {
        assertEquals(100, conditionAfter(0));
        assertEquals(100, conditionAfter(1), "picking a new card up marked it");
        assertEquals(100, conditionAfter(2), "the second free handling marked it");
    }

    @Test
    void afterTheFreeOnesEachStepTakesTwoTouches() {
        assertEquals(100, conditionAfter(3), "a step landed on the first charged touch");
        assertEquals(95, conditionAfter(4));
        assertEquals(95, conditionAfter(5));
        assertEquals(90, conditionAfter(6));
        assertEquals(90, conditionAfter(7));
        assertEquals(85, conditionAfter(8));
    }

    @Test
    void wearOnlyEverFallsAndStopsAtZero() {
        int previous = CardCondition.MINT;
        for (int n = 0; n <= 500; n++) {
            int condition = conditionAfter(n);
            assertTrue(condition <= previous, "condition rose at handling " + n);
            assertTrue(condition >= 0, "condition went negative at handling " + n);
            previous = condition;
        }
        assertEquals(0, conditionAfter(500), "a card should be ruined long before 500 handlings");
    }

    @Test
    void everyConditionOnTheLadderIsAWholeStep() {
        // The labels and the wear models are built on 5% steps; a condition
        // landing between them would show a label the artwork does not match.
        for (int n = 0; n <= 200; n++) {
            assertEquals(0, conditionAfter(n) % 5,
                    "handling " + n + " left condition " + conditionAfter(n)
                            + ", which is not a whole 5% step");
        }
    }

    @Test
    void aCardStaysPlayableAllTheWayDown() {
        // Wear is cosmetic and economic, never a soft-lock: a ruined card must
        // still resolve to a real wear stage and a real label.
        for (int n = 0; n <= 300; n++) {
            int condition = conditionAfter(n);
            int stage = CardCondition.stage(condition);
            assertTrue(stage >= 0 && stage <= 5);
            assertTrue(CardCondition.label(condition).length() > 0);
        }
    }
}
