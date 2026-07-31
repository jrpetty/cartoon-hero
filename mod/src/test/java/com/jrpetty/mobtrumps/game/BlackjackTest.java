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
 * <p>The one-use-per-stat rule is the load-bearing one. Without it a player just
 * calls the safest stat every time, never busts, and holds a 35% edge, which
 * makes the table a fragment faucet rather than a game.
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

    /** Of the stats left, the one least likely to bust from here. */
    private static Stat safest(Blackjack game) {
        int room = Blackjack.TARGET - game.playerTotal();
        Stat best = null;
        double bestRisk = Double.MAX_VALUE;
        for (Stat stat : game.availableStats()) {
            double risk = MobCards.bustChance(stat, room);
            if (risk < bestRisk) {
                bestRisk = risk;
                best = stat;
            }
        }
        return best;
    }

    @Test
    void aStatCanOnlyBeCalledOncePerHand() {
        Blackjack game = hand(1);
        Stat first = game.availableStats().iterator().next();
        game.hit(first);
        assertFalse(game.availableStats().contains(first),
                "a spent stat is still on offer");
        if (!game.isFinished()) {
            assertThrows(IllegalArgumentException.class, () -> game.hit(first),
                    "the same stat was accepted twice — without this rule the "
                            + "player just calls the safest stat forever");
        }
    }

    @Test
    void aHandNeverRunsPastSixCalls() {
        for (long seed = 0; seed < 2000; seed++) {
            Blackjack game = playOut(seed, Blackjack.TARGET);
            assertTrue(game.playerDraws().size() <= Stat.values().length,
                    "seed " + seed + " drew " + game.playerDraws().size() + " times");
            assertTrue(game.dealerDraws().size() <= Stat.values().length);
        }
    }

    @Test
    void runningOutOfStatsStandsYouAutomatically() {
        for (long seed = 0; seed < 500; seed++) {
            Blackjack game = hand(seed);
            while (game.canHit()) {
                game.hit(game.availableStats().iterator().next());
            }
            assertTrue(game.isFinished(),
                    "seed " + seed + " spent every stat but the hand is still open");
            assertFalse(game.canHit());
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
            List<Stat> used = new ArrayList<>();
            for (Blackjack.Draw d : draws) {
                assertFalse(used.contains(d.stat()), "the dealer reused " + d.stat());
                used.add(d.stat());
            }
            // it stops only when standing, bust, or out of stats
            if (!game.dealerBust() && game.dealerTotal() < Blackjack.DEALER_STANDS) {
                assertEquals(Stat.values().length, draws.size(),
                        "the dealer stopped on " + game.dealerTotal() + " with stats left");
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
            } else if (game.playerTotal() < game.dealerTotal()) {
                expected = Blackjack.Result.DEALER_WIN;
            } else {
                expected = Blackjack.Result.PUSH;
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
        double edge = (wins - losses) / (double) hands;
        assertTrue(pushes > hands / 40, "ties almost never happen: " + pushes);
        // a plain "stand on 18, pick the safest stat" player should be losing —
        // the edge that makes this a game is knowing the stat spreads
        assertTrue(edge < 0.0, "a naive policy is already winning (edge " + edge + ")");
        assertTrue(edge > -0.45, "a naive policy loses far too heavily (edge " + edge + ")");
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
    void theStatSpreadsTheScreenShowsAreTheOnesActuallyDealt() {
        for (Stat stat : Stat.values()) {
            int[] spread = MobCards.spread(stat);
            int total = 0;
            for (int count : spread) {
                total += count;
            }
            assertEquals(MobCards.ALL.size(), total,
                    stat + " spread does not account for every mob");
            double mean = 0;
            for (int v = 0; v < spread.length; v++) {
                mean += (double) v * spread[v];
            }
            assertEquals(mean / MobCards.ALL.size(), MobCards.averageOf(stat), 1e-9,
                    stat + " average disagrees with its own spread");
            assertEquals(0.0, MobCards.bustChance(stat, MobCards.maxOf(stat)), 1e-9,
                    stat + " reports a bust chance with room for its highest value");
            assertEquals(1.0, MobCards.bustChance(stat, -1), 1e-9);
        }
    }

    @Test
    void noSingleStatIsStrictlyTheBestCall() {
        // if one stat were both higher-scoring and safer than another, that other
        // stat would be dead weight and the choice would stop being a choice
        for (Stat a : Stat.values()) {
            boolean dominated = false;
            for (Stat b : Stat.values()) {
                if (a == b) {
                    continue;
                }
                boolean betterEverywhere = MobCards.averageOf(b) >= MobCards.averageOf(a);
                for (int room = 0; room <= 10 && betterEverywhere; room++) {
                    if (MobCards.bustChance(b, room) > MobCards.bustChance(a, room)) {
                        betterEverywhere = false;
                    }
                }
                if (betterEverywhere && MobCards.averageOf(b) > MobCards.averageOf(a)) {
                    dominated = true;
                }
            }
            assertFalse(dominated, a + " is beaten by another stat at every total — "
                    + "nobody would ever call it");
        }
    }

    @Test
    void attackIsTheNibbleAndFarmableIsTheGamble() {
        // the two ends the whole game hangs on, pinned so a card edit cannot
        // quietly flatten the choice
        int[] attack = MobCards.spread(Stat.ATTACK);
        assertTrue(attack[0] >= 20, "only " + attack[0] + " mobs have no Attack — "
                + "calling Attack on 20 is meant to be the safe nibble");
        assertTrue(MobCards.averageOf(Stat.FARMABLE) > MobCards.averageOf(Stat.ATTACK) + 2.0,
                "Farmable is meant to be the fast, risky climb");
        assertTrue(MobCards.bustChance(Stat.SPEED, 6) < MobCards.bustChance(Stat.FARMABLE, 6),
                "Speed is meant to be the safer hit than Farmable");
    }
}
