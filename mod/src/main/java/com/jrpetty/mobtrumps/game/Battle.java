package com.jrpetty.mobtrumps.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A Top Trumps battle between the player and the CPU.
 *
 * The deck is dealt evenly. Whoever holds the turn picks a stat on their
 * top card; the higher value wins both cards plus anything in the pot and
 * picks again. Ties send both cards to the pot. Take every card to win.
 */
public final class Battle {

    public enum Side { PLAYER, CPU, NONE }

    /** The outcome of a single resolved round. */
    public record RoundResult(int round, Side chooser, Stat stat,
                              MobCard playerCard, MobCard cpuCard, Side winner) {
    }

    public static final int MAX_ROUNDS = 500;

    private final Deque<MobCard> playerDeck = new ArrayDeque<>();
    private final Deque<MobCard> cpuDeck = new ArrayDeque<>();
    private final List<MobCard> pot = new ArrayList<>();
    private final RandomGenerator random;
    private Difficulty difficulty = Difficulty.NORMAL;
    private Side turn = Side.PLAYER;
    private int round = 0;
    private boolean finished = false;
    private Side winner = Side.NONE;
    /** Who won the coin flip that settled the most recent drawn round. */
    private Side lastCoin = Side.NONE;

    public Battle(int deckSize, RandomGenerator random) {
        this.random = random;
        deckSize = Math.max(4, Math.min(deckSize, MobCards.ALL.size()));
        if (deckSize % 2 != 0) deckSize--;
        List<MobCard> deck = MobCards.shuffledDeck(deckSize, random);
        for (int i = 0; i < deck.size(); i++) {
            (i < deck.size() / 2 ? playerDeck : cpuDeck).addLast(deck.get(i));
        }
    }

    /**
     * Deal explicit hands to each side (used for deck-builder battles and
     * deck-vs-deck duels). Both hands are shuffled; if one side is empty it
     * is filled with a random hand matching the other's size.
     */
    public Battle(List<MobCard> playerHand, List<MobCard> cpuHand, RandomGenerator random) {
        this.random = random;
        List<MobCard> p = new ArrayList<>(playerHand);
        List<MobCard> c = new ArrayList<>(cpuHand);
        int size = Math.max(2, Math.max(p.size(), c.size()));
        if (p.isEmpty()) p = MobCards.shuffledDeck(size, random);
        if (c.isEmpty()) c = MobCards.shuffledDeck(size, random);
        Collections.shuffle(p, java.util.Random.from(random));
        Collections.shuffle(c, java.util.Random.from(random));
        playerDeck.addAll(p);
        cpuDeck.addAll(c);
    }

    public boolean isFinished() {
        return finished;
    }

    public Side getWinner() {
        return winner;
    }

    public Side getTurn() {
        return turn;
    }

    /**
     * The side that won the coin flip settling the last round, or {@link
     * Side#NONE} if that round was not a draw. Reset at the start of every
     * round, so it always describes the round just played.
     */
    public Side lastCoin() {
        return lastCoin;
    }

    public int getRound() {
        return round;
    }

    public int playerCardCount() {
        return playerDeck.size();
    }

    public int cpuCardCount() {
        return cpuDeck.size();
    }

    public int potCount() {
        return pot.size();
    }

    /** The player's current top card (never null while the game runs). */
    public MobCard playerTopCard() {
        return playerDeck.peekFirst();
    }

    /** The CPU/opponent's current top card (used to prompt a human opponent). */
    public MobCard cpuTopCard() {
        return cpuDeck.peekFirst();
    }

    /** Play one round with the stat picked by whoever holds the turn. */
    public RoundResult playRound(Stat stat) {
        if (finished) throw new IllegalStateException("battle is already finished");
        if (stat == null) throw new IllegalArgumentException("a stat must be chosen");

        Side chooser = turn;
        MobCard playerCard = playerDeck.pollFirst();
        MobCard cpuCard = cpuDeck.pollFirst();
        round++;
        lastCoin = Side.NONE; // only a drawn round sets this

        // Rarity is a "lower wins" stat, so compare direction-aware scores
        int playerScore = stat.score(playerCard.stat(stat));
        int cpuScore = stat.score(cpuCard.stat(stat));

        Side roundWinner;
        if (playerScore > cpuScore) {
            roundWinner = Side.PLAYER;
            playerDeck.addLast(playerCard);
            playerDeck.addLast(cpuCard);
            pot.forEach(playerDeck::addLast);
            pot.clear();
            turn = Side.PLAYER;
        } else if (cpuScore > playerScore) {
            roundWinner = Side.CPU;
            cpuDeck.addLast(cpuCard);
            cpuDeck.addLast(playerCard);
            pot.forEach(cpuDeck::addLast);
            pot.clear();
            turn = Side.CPU;
        } else {
            // Tie: both cards go to the pot and nobody has earned the next pick.
            // Letting the chooser simply go again quietly rewards whoever picked
            // the drawn stat, so the turn is settled on a straight coin flip —
            // an even chance for each side. lastCoin lets the UI show it.
            roundWinner = Side.NONE;
            pot.add(playerCard);
            pot.add(cpuCard);
            turn = random.nextBoolean() ? Side.PLAYER : Side.CPU;
            lastCoin = turn;
        }

        checkEnd();
        return new RoundResult(round, chooser, stat, playerCard, cpuCard, roundWinner);
    }

    public void setDifficulty(Difficulty difficulty) {
        if (difficulty != null) this.difficulty = difficulty;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /** The stat the CPU will pick on its turn, according to the difficulty. */
    public Stat cpuChoice() {
        MobCard top = cpuDeck.peekFirst();
        if (top == null) return Stat.HEALTH;
        return switch (difficulty) {
            case EASY -> Stat.values()[random.nextInt(Stat.values().length)];
            case NORMAL -> top.bestStat();
            case HARD -> hardChoice(top);
        };
    }

    /** Lead with the stat that has the best odds of beating a random card, with a little bluff. */
    private Stat hardChoice(MobCard top) {
        Stat best = Stat.HEALTH;
        Stat second = Stat.HEALTH;
        double bestOdds = -1;
        double secondOdds = -1;
        for (Stat s : Stat.values()) {
            double odds = MobCards.winOdds(s, top.stat(s));
            if (odds > bestOdds) {
                second = best;
                secondOdds = bestOdds;
                best = s;
                bestOdds = odds;
            } else if (odds > secondOdds) {
                second = s;
                secondOdds = odds;
            }
        }
        // if the runner-up is nearly as strong, occasionally bluff with it so a human can't read the CPU
        if (secondOdds >= 0 && bestOdds - secondOdds < 0.15 && random.nextDouble() < 0.25) {
            return second;
        }
        return best;
    }

    private void checkEnd() {
        boolean playerOut = playerDeck.isEmpty();
        boolean cpuOut = cpuDeck.isEmpty();
        if (playerOut && cpuOut) {
            finished = true;
            winner = Side.NONE; // everything ended up in the pot: a draw
        } else if (playerOut) {
            finished = true;
            winner = Side.CPU;
        } else if (cpuOut) {
            finished = true;
            winner = Side.PLAYER;
        } else if (round >= MAX_ROUNDS) {
            finished = true; // stalemate: call it on cards held
            winner = playerDeck.size() > cpuDeck.size() ? Side.PLAYER
                    : cpuDeck.size() > playerDeck.size() ? Side.CPU : Side.NONE;
        }
    }
}
