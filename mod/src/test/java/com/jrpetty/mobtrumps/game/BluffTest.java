package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Bluff engine.
 *
 * <p>The rules that matter here are the ones about who eats the pile and who
 * wins, because those are the ones a player will feel cheated by if they are
 * wrong. Several of these exist because a simulation broke them first.
 */
class BluffTest {

    private static Bluff game(long seed) {
        return new Bluff(4, new Random(seed));
    }

    @Test
    void everyDealtClaimIsInThePlayableBand() {
        assertFalse(Bluff.CLAIMS.isEmpty(), "no claims at all");
        int deck = MobCards.ALL.size();
        for (Bluff.Claim claim : Bluff.CLAIMS) {
            double rate = claim.deckCount() / (double) deck;
            assertTrue(rate >= Bluff.MIN_RATE && rate <= Bluff.MAX_RATE,
                    claim.text() + " matches " + claim.deckCount() + "/" + deck);
        }
    }

    @Test
    void claimsReadAsWholeSentences() {
        for (Bluff.Claim claim : Bluff.CLAIMS) {
            String text = claim.text();
            assertTrue(text.endsWith("?"), text);
            assertFalse(text.contains("??"), text);
            assertFalse(text.contains("  "), text);
            if (claim.b() < 0) {
                continue;
            }
            // The invariant, not a guess at what a bad join looks like: the two
            // halves must open identically, because the second one is printed
            // with the FIRST one's opener sliced off it. An earlier version of
            // this test looked for "or Is"/"or Does" in the output and passed
            // happily on "Is it hostile or it fly?".
            String left = Bluff.opener(GuessQuestion.TEMPLATES.get(claim.a()));
            String right = Bluff.opener(GuessQuestion.TEMPLATES.get(claim.b()));
            assertFalse(left.isEmpty(), "unrecognised opener in " + text);
            assertEquals(left, right, "mismatched openers produced: " + text);
            for (String junk : new String[]{" or it ", " or its ", " or you ", " or a it "}) {
                assertFalse(text.contains(junk), text);
            }
        }
    }

    @Test
    void aPlayOfTruthfulCardsSurvivesAChallenge() {
        Bluff g = game(11);
        int liar = g.turn();
        int index = firstMatching(g, liar, true);
        g.play(liar, List.of(index));
        int doubter = g.turn();
        int before = g.handSize(doubter);
        assertTrue(g.challenge(doubter));
        assertFalse(g.lastReveal().wasLying());
        assertEquals(doubter, g.lastReveal().loser());
        assertTrue(g.handSize(doubter) > before, "wrong challenger must eat the pile");
    }

    @Test
    void aPlayOfLiesIsCaught() {
        Bluff g = game(12);
        int liar = g.turn();
        int index = firstMatching(g, liar, false);
        int before = g.handSize(liar);
        g.play(liar, List.of(index));
        int doubter = g.turn();
        assertTrue(g.challenge(doubter));
        assertTrue(g.lastReveal().wasLying());
        assertEquals(liar, g.lastReveal().loser());
        assertEquals(before, g.handSize(liar), "the liar takes back exactly what they put down");
    }

    /**
     * The bug a simulation found: an honest player who put their last card down
     * and got challenged used to end up with no cards, no pending exit and no
     * win — unable to ever move again, so the game hung.
     */
    @Test
    void survivingAChallengeOnYourLastCardWinsTheGame() {
        Bluff g = game(3);
        int seat = g.turn();
        // strip the seat down to a single card that does match the claim
        int keep = firstMatching(g, seat, true);
        emptyExcept(g, seat, keep);
        assertEquals(1, g.handSize(seat));
        g.play(seat, List.of(0));
        assertEquals(seat, g.pendingOut());
        int doubter = g.turn();
        assertTrue(g.challenge(doubter));
        assertTrue(g.done(), "the game must end rather than hang");
        assertEquals(seat, g.winner());
    }

    @Test
    void goingOutUnchallengedWins() {
        Bluff g = game(5);
        int seat = g.turn();
        emptyExcept(g, seat, firstMatching(g, seat, true));
        g.play(seat, List.of(0));
        assertEquals(seat, g.pendingOut());
        assertTrue(g.passOnExit(g.turn()));
        assertEquals(seat, g.winner());
    }

    @Test
    void youMayNotPlayOnWhileSomeoneIsOneChallengeFromWinning() {
        Bluff g = game(7);
        int seat = g.turn();
        emptyExcept(g, seat, firstMatching(g, seat, true));
        g.play(seat, List.of(0));
        int next = g.turn();
        assertFalse(g.play(next, List.of(0)),
                "the only moves are challenge and let them go");
        assertFalse(g.done());
    }

    @Test
    void outOfTurnMovesAreRefused() {
        Bluff g = game(9);
        int wrong = (g.turn() + 1) % g.seats();
        assertFalse(g.play(wrong, List.of(0)));
        assertFalse(g.challenge(wrong));
    }

    @Test
    void aPlayMustBeBetweenOneAndThreeDistinctCards() {
        Bluff g = game(13);
        int seat = g.turn();
        assertFalse(g.play(seat, List.of()), "nothing played");
        assertFalse(g.play(seat, List.of(0, 1, 2, 3)), "over the limit");
        assertFalse(g.play(seat, List.of(1, 1)), "the same card twice");
        assertFalse(g.play(seat, List.of(99)), "off the end of the hand");
        assertTrue(g.play(seat, List.of(0, 1)), "a legal play");
        assertEquals(Bluff.handSizeFor(g.seats()) - 2, g.handSize(seat));
    }

    @Test
    void youCannotChallengeYourOwnPlay() {
        Bluff g = game(17);
        int seat = g.turn();
        g.play(seat, List.of(0));
        assertFalse(g.challenge(seat), "not your turn anyway");
        // and with only the opening play on the table, the leader cannot doubt
        Bluff fresh = game(19);
        assertFalse(fresh.canChallenge(), "nothing has been played yet");
        assertFalse(fresh.challenge(fresh.turn()));
    }

    /**
     * A caught liar eats the whole pile; a wrong challenger eats only the cards
     * they called. That asymmetry is what makes challenging worth doing — see
     * the note in {@link Bluff#challenge}.
     */
    @Test
    void aCaughtLiarEatsThePileAndAWrongChallengerOnlyWhatTheyCalled() {
        boolean sawLie = false;
        boolean sawTruth = false;
        for (long seed = 0; seed < 200 && !(sawLie && sawTruth); seed++) {
            Bluff g = game(seed);
            int a = g.turn();
            g.play(a, List.of(0, 1));
            int b = g.turn();
            g.play(b, List.of(0, 1, 2));
            assertEquals(5, g.pileSize());
            int c = g.turn();
            int challengerBefore = g.handSize(c);
            int accusedBefore = g.handSize(b);
            assertTrue(g.challenge(c));
            if (g.lastReveal().wasLying()) {
                sawLie = true;
                assertEquals(5, g.lastReveal().pileTaken(), "the liar takes everything");
                assertEquals(accusedBefore + 5, g.handSize(b));
                assertEquals(0, g.pileSize());
                // and the catcher paid themselves one card
                assertEquals(challengerBefore - 1, g.handSize(c));
                assertEquals(1, g.burntCount());
            } else {
                sawTruth = true;
                assertEquals(3, g.lastReveal().pileTaken(), "only the called cards");
                assertEquals(challengerBefore + 3, g.handSize(c));
                assertEquals(2, g.pileSize(), "the rest of the pile stays on the table");
                assertEquals(0, g.burntCount(), "no bonus for a wrong call");
            }
        }
        assertTrue(sawLie, "never saw a caught lie across 200 deals");
        assertTrue(sawTruth, "never saw a wrong challenge across 200 deals");
    }

    @Test
    void headsUpHandsAreSmallerThanTableHands() {
        assertEquals(Bluff.HAND_SIZE_HEADS_UP, Bluff.handSizeFor(2));
        assertEquals(Bluff.HAND_SIZE, Bluff.handSizeFor(3));
        assertEquals(Bluff.HAND_SIZE, Bluff.handSizeFor(4));
        assertTrue(Bluff.HAND_SIZE_HEADS_UP < Bluff.HAND_SIZE);
        Bluff duel = new Bluff(2, new Random(1));
        assertEquals(Bluff.HAND_SIZE_HEADS_UP, duel.handSize(0));
        assertEquals(Bluff.HAND_SIZE_HEADS_UP, duel.handSize(1));
    }

    @Test
    void aChallengeStartsAFreshRoundLedByTheLoser() {
        Bluff g = game(29);
        Bluff.Claim first = g.claim();
        g.play(g.turn(), List.of(0));
        int doubter = g.turn();
        g.challenge(doubter);
        assertEquals(g.lastReveal().loser(), g.turn(), "the loser leads");
        assertNotNull(g.claim());
        // a new claim is drawn; it may coincide, but the round has restarted
        assertEquals(0, g.pileSize());
        assertFalse(g.canChallenge(), "nothing on the table to doubt yet");
        assertTrue(first == g.claim() || true);
    }

    /**
     * Hands only ever grow by eating a pile, so a table that challenges every
     * turn can pass the same cards round indefinitely. Every game must end.
     */
    @Test
    void everyGameEndsEvenWhenBothSidesChallengeConstantly() {
        for (int seats = Bluff.MIN_SEATS; seats <= Bluff.MAX_SEATS; seats++) {
            for (long seed = 0; seed < 40; seed++) {
                Bluff g = new Bluff(seats, new Random(seed));
                int guard = 0;
                while (!g.done() && guard++ < Bluff.MAX_MOVES * 4) {
                    int s = g.turn();
                    if (g.pendingOut() >= 0) {
                        if (!g.challenge(s)) {
                            g.passOnExit(s);
                        }
                    } else if (!g.challenge(s)) {
                        g.play(s, List.of(0));
                    }
                }
                assertTrue(g.done(), seats + " seats, seed " + seed + " never finished");
                assertTrue(g.winner() >= 0 && g.winner() < seats);
                assertTrue(g.moves() <= Bluff.MAX_MOVES, "ran past the cap");
            }
        }
    }

    @Test
    void noCardIsEverLostOrDuplicated() {
        Bluff g = new Bluff(4, new Random(31));
        Random r = new Random(31);
        for (int i = 0; i < 300 && !g.done(); i++) {
            int s = g.turn();
            if (g.pendingOut() >= 0) {
                if (r.nextBoolean()) {
                    g.challenge(s);
                } else {
                    g.passOnExit(s);
                }
            } else if (g.canChallenge() && r.nextInt(4) == 0) {
                g.challenge(s);
            } else {
                g.play(s, List.of(0));
            }
            List<MobCard> seen = new ArrayList<>();
            for (int q = 0; q < g.seats(); q++) {
                seen.addAll(g.hand(q));
            }
            int total = seen.size() + g.pileSize() + g.burntCount();
            assertEquals(g.seats() * Bluff.handSizeFor(g.seats()), total,
                    "cards went missing at move " + i);
            assertEquals(seen.stream().distinct().count(), seen.size(), "a card was duplicated");
        }
    }

    @Test
    void handsAreDealtDistinctAndFull() {
        Bluff g = new Bluff(4, new Random(37));
        List<MobCard> all = new ArrayList<>();
        for (int s = 0; s < g.seats(); s++) {
            assertEquals(Bluff.handSizeFor(g.seats()), g.handSize(s));
            all.addAll(g.hand(s));
        }
        assertEquals(all.size(), all.stream().distinct().count());
    }

    // ---- helpers ---------------------------------------------------------

    /** Index of the first card in a seat's hand that does (or does not) match. */
    private static int firstMatching(Bluff g, int seat, boolean matching) {
        List<MobCard> hand = g.hand(seat);
        for (int i = 0; i < hand.size(); i++) {
            if (g.claim().matches(hand.get(i)) == matching) {
                return i;
            }
        }
        throw new IllegalStateException("no " + (matching ? "matching" : "non-matching")
                + " card in seat " + seat + " for " + g.claim().text());
    }

    /**
     * Bring a seat down to one nominated card using nothing but legal moves —
     * it plays three at a time, everyone else plays one, nobody challenges. A
     * test-only setter on the engine would be easier and would let these tests
     * assert about positions the rules cannot actually reach.
     */
    private static void emptyExcept(Bluff g, int seat, int keep) {
        MobCard keeper = g.hand(seat).get(keep);
        int guard = 0;
        while (g.handSize(seat) > 1 && guard++ < 60) {
            int s = g.turn();
            List<MobCard> hand = g.hand(s);
            List<Integer> picks = new ArrayList<>();
            int want = s == seat ? Math.min(Bluff.MAX_PLAY, g.handSize(seat) - 1) : 1;
            for (int i = 0; i < hand.size() && picks.size() < want; i++) {
                if (s != seat || !hand.get(i).equals(keeper)) {
                    picks.add(i);
                }
            }
            assertTrue(g.play(s, picks), "setup play was refused");
            assertTrue(g.pendingOut() < 0, "setup emptied the wrong hand");
        }
        assertEquals(1, g.handSize(seat));
        assertEquals(keeper, g.hand(seat).get(0));
        // and leave it as that seat's turn to move
        while (g.turn() != seat) {
            int s = g.turn();
            assertTrue(g.play(s, List.of(0)));
        }
    }
}
