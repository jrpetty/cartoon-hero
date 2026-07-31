package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine has one property that matters above all the others: a battle that
 * starts must end. Top Trumps can cycle forever — two hands can trade the same
 * cards back and forth indefinitely — so the round cap exists, and a player
 * stuck in a game that never resolves would have no way out but to quit.
 *
 * <p>Card conservation is the other invariant worth pinning. Every card is
 * either in a hand or in the pot; if the total ever drifts, some round is
 * duplicating or eating cards, which is the kind of bug that only shows up
 * three hundred rounds into somebody's campaign.
 *
 * <p>These run thousands of complete battles across every difficulty, which is
 * cheap here precisely because the engine has no Minecraft in it.
 */
class BattleTest {

    /** Play a battle to its end, choosing as each side actually would. */
    private static Battle playOut(Battle battle, RandomGenerator random) {
        int guard = 0;
        while (!battle.isFinished()) {
            Stat stat = battle.getTurn() == Battle.Side.CPU
                    ? battle.cpuChoice()
                    : Stat.values()[random.nextInt(Stat.values().length)];
            battle.playRound(stat);
            if (++guard > Battle.MAX_ROUNDS + 50) {
                throw new AssertionError("battle ran past the round cap without finishing");
            }
        }
        return battle;
    }

    @Test
    void everyBattleTerminatesOnEveryDifficulty() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (boolean counting : new boolean[]{false, true}) {
                for (int seed = 0; seed < 250; seed++) {
                    RandomGenerator random = RandomGenerator.getDefault();
                    Battle battle = new Battle(16, new java.util.Random(seed));
                    battle.setDifficulty(difficulty);
                    battle.setCardCounting(counting);
                    playOut(battle, new java.util.Random(seed * 31L + 7));
                    assertTrue(battle.isFinished());
                    assertTrue(battle.getRound() <= Battle.MAX_ROUNDS,
                            "seed " + seed + " ran " + battle.getRound() + " rounds");
                    assertNotNull(battle.getWinner());
                }
            }
        }
    }

    @Test
    void aDrawOnlyHappensWhenItGenuinelyIsOne() {
        // NONE is a legitimate outcome, but only two situations may produce it:
        // every card ended up in the pot, or the round cap fell with the hands
        // dead level. A NONE arriving any other way means a side was declared
        // out while it still held cards, and the screen would say DRAW to
        // somebody who won.
        for (int seed = 0; seed < 500; seed++) {
            Battle battle = new Battle(16, new java.util.Random(seed));
            battle.setDifficulty(Difficulty.HARD);
            playOut(battle, new java.util.Random(seed));
            if (battle.getWinner() != Battle.Side.NONE) {
                continue;
            }
            boolean everythingInThePot =
                    battle.playerCardCount() == 0 && battle.cpuCardCount() == 0;
            boolean levelAtTheCap = battle.getRound() >= Battle.MAX_ROUNDS
                    && battle.playerCardCount() == battle.cpuCardCount();
            assertTrue(everythingInThePot || levelAtTheCap,
                    "seed " + seed + " drew after " + battle.getRound()
                            + " rounds holding " + battle.playerCardCount() + " v "
                            + battle.cpuCardCount() + " — that is not a draw");
        }
    }

    @Test
    void whoeverRunsOutOfCardsLoses() {
        for (int seed = 0; seed < 500; seed++) {
            Battle battle = new Battle(16, new java.util.Random(seed));
            battle.setDifficulty(Difficulty.NORMAL);
            playOut(battle, new java.util.Random(seed));
            if (battle.getWinner() == Battle.Side.PLAYER) {
                assertEquals(0, battle.cpuCardCount(),
                        "seed " + seed + ": the player won while the opponent held cards");
            } else if (battle.getWinner() == Battle.Side.CPU) {
                assertEquals(0, battle.playerCardCount(),
                        "seed " + seed + ": the opponent won while the player held cards");
            }
        }
    }

    @Test
    void theRoundCapSitsAboveWhatPlayActuallyReaches() {
        // The cap is a backstop, not a routine ending: if ordinary battles were
        // bumping into it, games would be decided by a timeout rather than by
        // running an opponent out of cards. Measured across the difficulties,
        // the longest observed battle is comfortably short of the cap.
        // A cap that is absent or absurd is the same as no cap at all: a cyclic
        // battle would spin until the player force-quit, and no random seed
        // would ever reveal it.
        assertTrue(Battle.MAX_ROUNDS > 0 && Battle.MAX_ROUNDS <= 2000,
                "MAX_ROUNDS is " + Battle.MAX_ROUNDS + ", which is not a usable backstop");
        int longest = 0;
        for (Difficulty difficulty : Difficulty.values()) {
            for (int seed = 0; seed < 400; seed++) {
                Battle battle = new Battle(16, new java.util.Random(seed));
                battle.setDifficulty(difficulty);
                playOut(battle, new java.util.Random(seed * 7L + 3));
                longest = Math.max(longest, battle.getRound());
            }
        }
        assertTrue(longest < Battle.MAX_ROUNDS,
                "a battle reached " + longest + " rounds against a cap of "
                        + Battle.MAX_ROUNDS + " — the cap is now load-bearing");
        assertTrue(Battle.MAX_ROUNDS >= longest * 5 / 4,
                "the cap (" + Battle.MAX_ROUNDS + ") leaves little headroom over the "
                        + "longest observed battle (" + longest + ")");
    }

    @Test
    void noCardIsEverCreatedOrDestroyed() {
        for (int seed = 0; seed < 200; seed++) {
            RandomGenerator picker = new java.util.Random(seed);
            Battle battle = new Battle(16, new java.util.Random(seed));
            int total = battle.playerCardCount() + battle.cpuCardCount() + battle.potCount();
            assertEquals(16, total, "a 16-card battle did not start with 16 cards");
            while (!battle.isFinished()) {
                battle.playRound(battle.getTurn() == Battle.Side.CPU
                        ? battle.cpuChoice()
                        : Stat.values()[picker.nextInt(Stat.values().length)]);
                assertEquals(total,
                        battle.playerCardCount() + battle.cpuCardCount() + battle.potCount(),
                        "card count drifted at round " + battle.getRound());
            }
        }
    }

    @Test
    void theSameSeedAlwaysPlaysTheSameBattle() {
        // Campaign missions promise a fixed opponent; a battle that varies
        // between runs of the same seed would make that untrue.
        for (int seed = 0; seed < 50; seed++) {
            List<String> first = trace(seed);
            List<String> second = trace(seed);
            assertEquals(first, second, "seed " + seed + " replayed differently");
        }
    }

    private static List<String> trace(int seed) {
        RandomGenerator picker = new java.util.Random(seed);
        Battle battle = new Battle(16, new java.util.Random(seed));
        battle.setDifficulty(Difficulty.HARD);
        battle.setCardCounting(true);
        List<String> out = new ArrayList<>();
        while (!battle.isFinished()) {
            Stat stat = battle.getTurn() == Battle.Side.CPU
                    ? battle.cpuChoice()
                    : Stat.values()[picker.nextInt(Stat.values().length)];
            Battle.RoundResult r = battle.playRound(stat);
            out.add(r.round() + ":" + r.stat() + ":" + r.winner()
                    + ":" + r.playerCard().id() + "/" + r.cpuCard().id());
        }
        out.add("winner=" + battle.getWinner());
        return out;
    }

    @Test
    void theOpponentAlwaysPicksAStatItActuallyHolds() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (int seed = 0; seed < 100; seed++) {
                Battle battle = new Battle(16, new java.util.Random(seed));
                battle.setDifficulty(difficulty);
                RandomGenerator picker = new java.util.Random(seed);
                while (!battle.isFinished()) {
                    Stat choice = battle.cpuChoice();
                    assertNotNull(choice, difficulty + " opponent chose no stat");
                    battle.playRound(battle.getTurn() == Battle.Side.CPU
                            ? choice
                            : Stat.values()[picker.nextInt(Stat.values().length)]);
                }
            }
        }
    }

    @Test
    void aFinishedBattleRefusesToPlayOn() {
        Battle battle = new Battle(16, new java.util.Random(1));
        playOut(battle, new java.util.Random(1));
        assertTrue(battle.isFinished());
        try {
            battle.playRound(Stat.HEALTH);
            throw new AssertionError("a finished battle accepted another round");
        } catch (IllegalStateException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}
