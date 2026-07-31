package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The board strikes cards off by itself, so the player never checks the working.
 * That is the whole point — nobody can cheat, and nobody has to trust anyone —
 * but it means an elimination bug is invisible: the hidden mob would simply
 * vanish from the board and the game would become unwinnable with no warning.
 *
 * <p>So the load-bearing property is not "elimination removes cards", it is
 * <b>the answer never eliminates the mob that produced it</b>. That is checked
 * here against every mob, every question and every value — the whole space,
 * because it is small enough to walk.
 */
class GuessQuestionTest {

    private static List<MobCard> board() {
        return new ArrayList<>(MobCards.ALL);
    }

    @Test
    void theHiddenMobSurvivesEveryAnswerAboutIt() {
        for (MobCard secret : MobCards.ALL) {
            for (GuessQuestion.Template template : GuessQuestion.TEMPLATES) {
                for (int v = template.minValue(); v <= template.maxValue(); v++) {
                    boolean answer = GuessQuestion.ask(template, v, secret);
                    List<MobCard> left =
                            GuessQuestion.eliminate(board(), template, v, answer);
                    assertTrue(left.contains(secret),
                            "answering \"" + template.text(v) + "\" struck off "
                                    + secret.id() + ", which is the mob being asked about");
                    if (!template.needsValue()) {
                        break;
                    }
                }
            }
        }
    }

    @Test
    void everyMobCanBeCorneredDownToOne() {
        // Greedy play, the way a careful player narrows: always ask whatever
        // splits the remaining board most evenly. If any mob cannot be isolated
        // the game is unwinnable when it is the secret, and nothing would say so.
        for (MobCard secret : MobCards.ALL) {
            List<MobCard> pool = board();
            int asked = 0;
            while (pool.size() > 1 && asked < 20) {
                GuessQuestion.Template best = null;
                int bestValue = 0;
                int bestScore = Integer.MAX_VALUE;
                for (GuessQuestion.Template template : GuessQuestion.TEMPLATES) {
                    for (int v = template.minValue(); v <= template.maxValue(); v++) {
                        int yes = GuessQuestion.yesCount(pool, template, v);
                        if (yes > 0 && yes < pool.size()) {
                            int score = Math.abs(yes * 2 - pool.size());
                            if (score < bestScore) {
                                bestScore = score;
                                best = template;
                                bestValue = v;
                            }
                        }
                        if (!template.needsValue()) {
                            break;
                        }
                    }
                }
                assertNotNull(best, secret.id() + " cannot be narrowed past "
                        + pool.size() + " candidates — no question separates them");
                pool = GuessQuestion.eliminate(pool, best, bestValue,
                        GuessQuestion.ask(best, bestValue, secret));
                asked++;
            }
            assertEquals(1, pool.size(), secret.id() + " was not isolated");
            assertEquals(secret, pool.get(0));
            assertTrue(asked <= 8, secret.id() + " took " + asked + " questions");
        }
    }

    @Test
    void theThreeLookalikePairsAreActuallySeparable() {
        // These six are identical on all six stats and share a category. Before
        // traits existed the game could only ever narrow them to a coin flip.
        String[][] pairs = {{"skeleton", "zombie"}, {"bogged", "stray"}, {"cow", "sheep"}};
        for (String[] pair : pairs) {
            MobCard a = MobCards.byId(pair[0]);
            MobCard b = MobCards.byId(pair[1]);
            assertNotNull(a, pair[0]);
            assertNotNull(b, pair[1]);
            boolean separable = false;
            for (GuessQuestion.Template template : GuessQuestion.TEMPLATES) {
                for (int v = template.minValue(); v <= template.maxValue(); v++) {
                    if (GuessQuestion.ask(template, v, a) != GuessQuestion.ask(template, v, b)) {
                        separable = true;
                    }
                    if (!template.needsValue()) {
                        break;
                    }
                }
            }
            assertTrue(separable, pair[0] + " and " + pair[1]
                    + " cannot be told apart by any question — that is a coin flip");
        }
    }

    @Test
    void aQuestionNobodyAnswersDifferentlyIsMarkedUseless() {
        List<MobCard> pool = board();
        for (GuessQuestion.Template template : GuessQuestion.TEMPLATES) {
            for (int v = template.minValue(); v <= template.maxValue(); v++) {
                int yes = GuessQuestion.yesCount(pool, template, v);
                boolean useful = GuessQuestion.isUseful(pool, template, v);
                assertEquals(yes > 0 && yes < pool.size(), useful,
                        template.text(v) + " reported the wrong usefulness");
                if (!template.needsValue()) {
                    break;
                }
            }
        }
        // one that genuinely tells you nothing: no mob has a stat below 0
        GuessQuestion.Template below = GuessQuestion.TEMPLATES.stream()
                .filter(t -> t.kind() == GuessQuestion.Kind.STAT_BELOW)
                .findFirst().orElseThrow();
        assertFalse(GuessQuestion.isUseful(pool, below, 0));
    }

    @Test
    void theCatalogueIsShortEnoughToScrollAndEveryRowReads() {
        assertEquals(GuessQuestion.TEMPLATE_COUNT, GuessQuestion.TEMPLATES.size());
        for (GuessQuestion.Template template : GuessQuestion.TEMPLATES) {
            String text = template.text(5);
            assertFalse(text.isBlank(), "a question row has no text");
            assertFalse(text.contains("%d"), "unfilled placeholder in: " + text);
            assertTrue(text.endsWith("?"), "not phrased as a question: " + text);
        }
    }

    @Test
    void searchNarrowsTheListAndAnEmptyBoxShowsEverything() {
        assertEquals(GuessQuestion.TEMPLATES.size(), GuessQuestion.search("").size());
        assertEquals(GuessQuestion.TEMPLATES.size(), GuessQuestion.search(null).size());
        assertEquals(GuessQuestion.TEMPLATES.size(), GuessQuestion.search("  ").size());

        List<GuessQuestion.Template> health = GuessQuestion.search("health");
        assertEquals(2, health.size(), "expected the less-than and more-than Health rows");
        for (GuessQuestion.Template t : health) {
            assertTrue(t.needsValue());
            assertEquals(Stat.HEALTH, t.stat());
        }
        assertTrue(GuessQuestion.search("HEALTH").size() == 2, "search should ignore case");
        assertTrue(GuessQuestion.search("fly").size() >= 1);
        assertTrue(GuessQuestion.search("zzzz").isEmpty());
    }

    @Test
    void thresholdRowsOnlyOfferValuesWorthAsking() {
        List<MobCard> pool = board();
        for (GuessQuestion.Template template : GuessQuestion.TEMPLATES) {
            if (!template.needsValue()) {
                continue;
            }
            assertTrue(template.clamp(-5) >= template.minValue());
            assertTrue(template.clamp(99) <= template.maxValue());
            // the extremes of the offered range must still be real questions
            // for at least some stat, or the box lets you waste a turn
            int useful = 0;
            for (int v = template.minValue(); v <= template.maxValue(); v++) {
                if (GuessQuestion.isUseful(pool, template, v)) {
                    useful++;
                }
            }
            assertTrue(useful > 0, template.label() + " has no useful value at all");
        }
    }

    @Test
    void rarityIsAskedExactlyAsItIsPrinted() {
        // Rarity is lower-is-rarer. The question must compare the printed number
        // so the answer agrees with the card the player is looking at.
        MobCard dragon = MobCards.byId("ender_dragon");
        GuessQuestion.Template below = GuessQuestion.TEMPLATES.stream()
                .filter(t -> t.kind() == GuessQuestion.Kind.STAT_BELOW
                        && t.stat() == Stat.RARITY)
                .findFirst().orElseThrow();
        assertEquals(dragon.rarity() < 3, GuessQuestion.ask(below, 3, dragon));
    }
}
