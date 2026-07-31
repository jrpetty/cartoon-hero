package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wagering game has to be checked as a wagering game: the rules being right is
 * not enough if the odds are wrong. The engine here is Minecraft-free, so both
 * are measured rather than argued about — the rules exactly, over every path,
 * and the odds over hundreds of thousands of hands.
 *
 * <p>Stats may be called again, which by itself hands the player a large edge —
 * you can keep calling the safest one. The dealer is set against that rather
 * than against a casino's rules: it draws to {@value Blackjack#DEALER_STANDS}
 * and takes ties. Those two numbers are the balance, so they are measured here
 * over tens of thousands of hands rather than assumed.
 */
class BlackjackTest {

    private static Blackjack hand(long seed) {
        return new Blackjack(new Random(seed));
    }

    /** Play out a hand with a simple policy, returning the finished game. */
    private static Blackjack playOut(long seed, int standOn) {
        Blackjack game = hand(seed);
        while (game.phase() == Blackjack.Phase.PLAYER && game.playerTotal() < standOn
                && game.canHit()) {
            Stat pick = safest(game);
            game.hit(pick);
        }
        if (game.phase() == Blackjack.Phase.PLAYER) {
            game.stand();
        }
        return game;
    }

    /** The stat whose average most nearly fills the gap — a sane player's call. */
    private static Stat safest(Blackjack game) {
        int room = Blackjack.TARGET - game.playerTotal();
        Stat best = null;
        double bestGap = Double.MAX_VALUE;
        for (Stat stat : game.availableStats()) {
            double gap = Math.abs(MobCards.averageOf(stat) - room);
            if (gap < bestGap) {
                bestGap = gap;
                best = stat;
            }
        }
        return best;
    }

    @Test
    void thesSameStatCanBeCalledAgain() {
        Blackjack game = hand(1);
        game.hit(Stat.ATTACK);
        assertTrue(game.availableStats().contains(Stat.ATTACK),
                "a called stat was taken off the table — repeats are allowed now");
        if (!game.isFinished()) {
            game.hit(Stat.ATTACK);
            assertEquals(2, game.playerDraws().size(), "the second call did not land");
        }
    }

    @Test
    void aHandAlwaysEnds() {
        // Attack is 0 on thirty of the eighty-one mobs, so with repeats allowed a
        // total can sit still; the draw cap is what guarantees a hand terminates
        for (long seed = 0; seed < 1500; seed++) {
            Blackjack game = hand(seed);
            int guard = 0;
            while (game.canHit()) {
                game.hit(Stat.ATTACK);
                if (++guard > Blackjack.MAX_DRAWS + 4) {
                    throw new AssertionError("seed " + seed + " never stopped drawing");
                }
            }
            assertTrue(game.isFinished(), "seed " + seed + " hit the cap but stayed open");
            assertTrue(game.playerDraws().size() <= Blackjack.MAX_DRAWS);
            assertTrue(game.dealerDraws().size() <= Blackjack.MAX_DRAWS);
        }
    }

    @Test
    void goingOverTwentyOneLosesImmediately() {
        int busts = 0;
        for (long seed = 0; seed < 3000; seed++) {
            Blackjack game = hand(seed);
            while (game.canHit()) {
                game.hit(game.availableStats().iterator().next());
                if (game.playerBust()) {
                    busts++;
                    assertTrue(game.isFinished(), "a bust hand kept playing");
                    assertEquals(Blackjack.Result.DEALER_WIN, game.result(),
                            "busting did not lose the hand");
                    assertTrue(game.dealerDraws().isEmpty(),
                            "the dealer played on after the player had already bust");
                    break;
                }
            }
        }
        assertTrue(busts > 100, "only " + busts + " busts in 3000 hands — suspicious");
    }

    @Test
    void everyDrawAddsExactlyThatCardsValueOnThatStat() {
        for (long seed = 0; seed < 800; seed++) {
            Blackjack game = playOut(seed, 18);
            int running = 0;
            for (Blackjack.Draw draw : game.playerDraws()) {
                MobCard card = MobCards.byId(draw.cardId());
                assertNotNull(card, "unknown card " + draw.cardId());
                assertEquals(card.stat(draw.stat()), draw.value(),
                        "the value taken was not the card's " + draw.stat());
                running += draw.value();
                assertEquals(running, draw.total(), "the running total drifted");
            }
            assertEquals(running, game.playerTotal());
        }
    }

    @Test
    void theDealerObeysItsOwnRule() {
        for (long seed = 0; seed < 2000; seed++) {
            Blackjack game = playOut(seed, 17);
            if (game.playerBust()) {
                continue;
            }
            List<Blackjack.Draw> draws = game.dealerDraws();
            // it must never have drawn while already standing pat
            for (int i = 0; i < draws.size(); i++) {
                int before = i == 0 ? 0 : draws.get(i - 1).total();
                assertTrue(before < Blackjack.DEALER_STANDS,
                        "the dealer drew on " + before + ", at or past its stand of "
                                + Blackjack.DEALER_STANDS);
            }
            // and never called the same stat twice
            // it stops only when standing, bust, or out of draws
            if (!game.dealerBust() && game.dealerTotal() < Blackjack.DEALER_STANDS) {
                assertEquals(Blackjack.MAX_DRAWS, draws.size(),
                        "the dealer stopped on " + game.dealerTotal() + " early");
            }
        }
    }

    @Test
    void theHandIsSettledTheWayTheTableSaysItShouldBe() {
        for (long seed = 0; seed < 4000; seed++) {
            Blackjack game = playOut(seed, 18);
            assertTrue(game.isFinished());
            Blackjack.Result expected;
            if (game.playerBust()) {
                expected = Blackjack.Result.DEALER_WIN;
            } else if (game.dealerBust() || game.playerTotal() > game.dealerTotal()) {
                expected = Blackjack.Result.PLAYER_WIN;
            } else {
                expected = Blackjack.Result.DEALER_WIN; // the house takes ties
            }
            assertEquals(expected, game.result(), "seed " + seed + ": player "
                    + game.playerTotal() + " v dealer " + game.dealerTotal());
        }
    }

    @Test
    void theSameSeedDealsTheSameHand() {
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(trace(seed), trace(seed), "seed " + seed + " dealt differently");
        }
    }

    private static String trace(long seed) {
        Blackjack game = playOut(seed, 18);
        StringBuilder sb = new StringBuilder();
        for (Blackjack.Draw d : game.playerDraws()) {
            sb.append(d.stat()).append(':').append(d.cardId()).append(':').append(d.value()).append(' ');
        }
        return sb.append('|').append(game.playerTotal()).append('/')
                .append(game.dealerTotal()).append('/').append(game.result()).toString();
    }

    @Test
    void theShoeNeverRunsDryOverALongSession() {
        // one shoe across many hands, which is where a reshuffle bug would show
        Random rng = new Random(99);
        for (int handNo = 0; handNo < 5000; handNo++) {
            Blackjack game = new Blackjack(rng);
            while (game.canHit() && game.playerTotal() < 18) {
                game.hit(safest(game));
            }
            if (!game.isFinished()) {
                game.stand();
            }
            for (Blackjack.Draw d : game.playerDraws()) {
                assertNotNull(MobCards.byId(d.cardId()), "dealt a card that does not exist");
            }
        }
    }

    @Test
    void theOddsAreCloseEnoughToFairToWagerOn() {
        // measured, because a wagering game with a hidden edge is a broken one
        int wins = 0;
        int losses = 0;
        int pushes = 0;
        final int hands = 60000;
        for (long seed = 0; seed < hands; seed++) {
            Blackjack game = playOut(seed, 18);
            switch (game.result()) {
                case PLAYER_WIN -> wins++;
                case DEALER_WIN -> losses++;
                default -> pushes++;
            }
        }
        assertEquals(hands, wins + losses + pushes);
        assertEquals(0, pushes, "the house takes ties, so there should be none");
        double edge = (wins - losses) / (double) hands;
        // the whole balance is DEALER_STANDS plus ties going to the house. A
        // plain policy should be a little behind; if this drifts positive the
        // table has become a fragment faucet, and far negative it is unplayable.
        assertTrue(edge < 0.02, "a plain policy is winning (edge " + edge + ") — "
                + "the table is now a faucet");
        assertTrue(edge > -0.35, "a plain policy loses far too heavily (edge " + edge + ")");
    }

    @Test
    void callingAStatOutOfTurnIsRefused() {
        Blackjack game = hand(3);
        game.stand();
        assertTrue(game.isFinished());
        assertThrows(IllegalStateException.class, () -> game.hit(Stat.HEALTH));
        assertFalse(game.canHit());
    }

    @Test
    void attackIsTheNibbleAndFarmableIsTheGamble() {
        // the two ends the whole game hangs on, pinned so a card edit cannot
        // quietly flatten the choice
        int zeros = 0;
        for (MobCard card : MobCards.ALL) {
            if (card.stat(Stat.ATTACK) == 0) {
                zeros++;
            }
        }
        assertTrue(zeros >= 20, "only " + zeros + " mobs have no Attack — calling "
                + "Attack on 20 is meant to be the safe nibble");
        assertTrue(MobCards.averageOf(Stat.FARMABLE) > MobCards.averageOf(Stat.ATTACK) + 2.0,
                "Farmable is meant to be the fast, risky climb");
    }
}
