package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The condition ladder is small enough to check exhaustively, so it is checked
 * exhaustively — every one of the 101 legal percentages, plus the out-of-range
 * inputs that a corrupt save or an old card could produce.
 *
 * <p>{@link CardCondition#stage} matters more than it looks: it selects the
 * item model override, and the override table only has entries for 0-5. A
 * stage outside that range renders the card as a missing model, and nothing
 * logs a warning when it happens.
 */
class CardConditionTest {

    @Test
    void stageIsAlwaysAValidOverrideIndex() {
        for (int c = -50; c <= 150; c++) {
            int stage = CardCondition.stage(c);
            assertTrue(stage >= 0 && stage <= 5,
                    "condition " + c + " produced out-of-range wear stage " + stage);
        }
    }

    @Test
    void stageNeverImprovesAsConditionFalls() {
        for (int c = 1; c <= 100; c++) {
            assertTrue(CardCondition.stage(c) <= CardCondition.stage(c - 1),
                    "stage went down between condition " + (c - 1) + " and " + c);
        }
    }

    @Test
    void mintIsStageZeroAndRuinedIsStageFive() {
        assertEquals(0, CardCondition.stage(CardCondition.MINT));
        assertEquals(5, CardCondition.stage(0));
    }

    @Test
    void everyStageIsActuallyReachable() {
        boolean[] seen = new boolean[6];
        for (int c = 0; c <= 100; c++) {
            seen[CardCondition.stage(c)] = true;
        }
        for (int s = 0; s < seen.length; s++) {
            assertTrue(seen[s], "wear stage " + s + " has a model but no condition maps to it");
        }
    }

    @Test
    void labelAndColourAreTotal() {
        for (int c = -50; c <= 150; c++) {
            assertNotNull(CardCondition.label(c), "no label for condition " + c);
            assertTrue(CardCondition.label(c).length() > 0, "empty label at " + c);
            CardCondition.color(c);
        }
    }

    @Test
    void clampKeepsConditionInRange() {
        for (int c = -500; c <= 500; c++) {
            int v = CardCondition.clamp(c);
            assertTrue(v >= 0 && v <= 100, "clamp(" + c + ") = " + v);
        }
        assertEquals(100, CardCondition.clamp(101));
        assertEquals(0, CardCondition.clamp(-1));
    }
}
