package com.jrpetty.mobtrumps.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private Side turn = Side.PLAYER;
    private int round = 0;
    private boolean finished = false;
    private Side winner = Side.NONE;

    public Battle(int deckSize, RandomGenerator random) {
        deckSize = Math.max(4, Math.min(deckSize, MobCards.ALL.size()));
        if (deckSize % 2 != 0) deckSize--;
        List<MobCard> deck = MobCards.shuffledDeck(deckSize, random);
        for (int i = 0; i < deck.size(); i++) {
            (i < deck.size() / 2 ? playerDeck : cpuDeck).addLast(deck.get(i));
        }
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

        Side roundWinner;
        if (playerCard.stat(stat) > cpuCard.stat(stat)) {
            roundWinner = Side.PLAYER;
            playerDeck.addLast(playerCard);
            playerDeck.addLast(cpuCard);
            pot.forEach(playerDeck::addLast);
            pot.clear();
            turn = Side.PLAYER;
        } else if (cpuCard.stat(stat) > playerCard.stat(stat)) {
            roundWinner = Side.CPU;
            cpuDeck.addLast(cpuCard);
            cpuDeck.addLast(playerCard);
            pot.forEach(cpuDeck::addLast);
            pot.clear();
            turn = Side.CPU;
        } else {
            roundWinner = Side.NONE; // tie: cards to the pot, chooser goes again
            pot.add(playerCard);
            pot.add(cpuCard);
        }

        checkEnd();
        return new RoundResult(round, chooser, stat, playerCard, cpuCard, roundWinner);
    }

    /** The stat the CPU will pick on its turn: its top card's strongest. */
    public Stat cpuChoice() {
        MobCard top = cpuDeck.peekFirst();
        return top == null ? Stat.HEALTH : top.bestStat();
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
