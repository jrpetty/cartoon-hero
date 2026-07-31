package com.jrpetty.mobtrumps.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Twenty-One, played on mob cards.
 *
 * <p>You call a stat <em>before</em> the card is turned over. Call Health, the
 * card comes up Warden, and you take its 10. Call Speed next and turn a Cat for
 * 4, and you are on 14. Nearest to {@value #TARGET} without going over wins.
 *
 * <p>What makes it a game rather than a coin flip is that the six stats are not
 * the same bet. Farmable averages 5.8 and Rarity 5.5, so they climb fast and
 * bust often; Speed averages 3.9 but almost never lands high, which makes it the
 * safest real hit; and <b>thirty of the eighty-one mobs have no Attack at all</b>,
 * so calling Attack on 20 is the nibble that might cost you nothing. Choosing a
 * stat is choosing a distribution, and that is the whole skill.
 *
 * <p><b>Each stat may be called once per hand.</b> Without that rule a player
 * simply calls the safest stat every time and the game falls apart — measured at
 * a 35% edge, which is not a game, it is a faucet. One use each also makes the
 * arithmetic elegant: the six means add up to 24.3, comfortably past 21, so a
 * hand is really the question of which four or five of your six to spend.
 *
 * <p>Cards come from a shoe of all 81 dealt without replacement, not from the
 * player's own deck. A deck you assemble yourself could be stacked with
 * zero-Attack mobs and the bet stops being a bet.
 *
 * <p>Pure logic, no Minecraft — so the odds above are measured rather than
 * hoped for, in {@code BlackjackTest}.
 */
public final class Blackjack {

    public static final int TARGET = 21;

    /**
     * The dealer draws below this. Higher than a casino's 17 because the dealer
     * is under the same one-use rule and runs out of stats: at 17 it stood too
     * low too often and the player held a 22% edge. At 19 the game is close to
     * even and the dealer busts about a fifth of the time.
     */
    public static final int DEALER_STANDS = 19;

    /** Reshuffle the shoe below this many cards so a hand never runs dry. */
    private static final int RESHUFFLE_AT = 12;

    /** One revealed card and what calling that stat was worth. */
    public record Draw(Stat stat, String cardId, int value, int total) {
    }

    public enum Phase {
        /** The player is calling stats. */
        PLAYER,
        /** The player has stood; the dealer is playing out. */
        DEALER,
        /** Settled. */
        DONE
    }

    public enum Result { NONE, PLAYER_WIN, DEALER_WIN, PUSH }

    private final RandomGenerator random;
    private final Deque<MobCard> shoe = new ArrayDeque<>();
    private final List<Draw> playerDraws = new ArrayList<>();
    private final List<Draw> dealerDraws = new ArrayList<>();
    private final Set<Stat> playerUsed = EnumSet.noneOf(Stat.class);
    private final Set<Stat> dealerUsed = EnumSet.noneOf(Stat.class);
    private int playerTotal;
    private int dealerTotal;
    private Phase phase = Phase.PLAYER;
    private Result result = Result.NONE;

    public Blackjack(RandomGenerator random) {
        this.random = random;
        refillShoe();
    }

    private void refillShoe() {
        List<MobCard> cards = new ArrayList<>(MobCards.ALL);
        Collections.shuffle(cards, java.util.Random.from(random));
        shoe.clear();
        shoe.addAll(cards);
    }

    private MobCard deal() {
        if (shoe.size() < RESHUFFLE_AT) {
            refillShoe();
        }
        return shoe.pollFirst();
    }

    // --- what the player may do -------------------------------------------

    /** Stats the player has not spent yet. Empty means they must stand. */
    public Set<Stat> availableStats() {
        Set<Stat> left = EnumSet.allOf(Stat.class);
        left.removeAll(playerUsed);
        return left;
    }

    public boolean canHit() {
        return phase == Phase.PLAYER && !availableStats().isEmpty();
    }

    /**
     * Call a stat: a card is turned over and its value on that stat is added.
     * Going past {@value #TARGET} settles the hand immediately — the dealer
     * does not need to play, exactly as in blackjack.
     */
    public Draw hit(Stat stat) {
        if (phase != Phase.PLAYER) {
            throw new IllegalStateException("not the player's turn");
        }
        if (stat == null || playerUsed.contains(stat)) {
            throw new IllegalArgumentException("that stat has already been called");
        }
        playerUsed.add(stat);
        MobCard card = deal();
        int value = card.stat(stat);
        playerTotal += value;
        Draw draw = new Draw(stat, card.id(), value, playerTotal);
        playerDraws.add(draw);
        if (playerTotal > TARGET) {
            phase = Phase.DONE;
            result = Result.DEALER_WIN;
        } else if (availableStats().isEmpty()) {
            stand();
        }
        return draw;
    }

    /** Stop drawing and let the dealer play out. */
    public void stand() {
        if (phase != Phase.PLAYER) {
            return;
        }
        phase = Phase.DEALER;
        playDealer();
        settle();
        phase = Phase.DONE;
    }

    // --- the dealer --------------------------------------------------------

    /**
     * The stat the dealer will call next, or null when it stands.
     *
     * <p>It aims at {@value #TARGET}: of the stats it has left, it takes the one
     * whose average most nearly covers the gap. Deliberately simple, so a player
     * watching can work out what it is doing and play against it.
     */
    public Stat dealerChoice() {
        if (dealerTotal >= DEALER_STANDS) {
            return null;
        }
        Set<Stat> left = EnumSet.allOf(Stat.class);
        left.removeAll(dealerUsed);
        if (left.isEmpty()) {
            return null;
        }
        int need = TARGET - dealerTotal;
        Stat best = null;
        double bestGap = Double.MAX_VALUE;
        for (Stat stat : left) {
            double gap = Math.abs(MobCards.averageOf(stat) - need);
            if (gap < bestGap) {
                bestGap = gap;
                best = stat;
            }
        }
        return best;
    }

    private void playDealer() {
        Stat choice;
        while ((choice = dealerChoice()) != null) {
            dealerUsed.add(choice);
            MobCard card = deal();
            int value = card.stat(choice);
            dealerTotal += value;
            dealerDraws.add(new Draw(choice, card.id(), value, dealerTotal));
            if (dealerTotal > TARGET) {
                return;
            }
        }
    }

    private void settle() {
        if (playerTotal > TARGET) {
            result = Result.DEALER_WIN;
        } else if (dealerTotal > TARGET || playerTotal > dealerTotal) {
            result = Result.PLAYER_WIN;
        } else if (playerTotal < dealerTotal) {
            result = Result.DEALER_WIN;
        } else {
            // a tie returns the stake, as it does at a real table
            result = Result.PUSH;
        }
    }

    // --- reading the table --------------------------------------------------

    public Phase phase() {
        return phase;
    }

    public Result result() {
        return result;
    }

    public boolean isFinished() {
        return phase == Phase.DONE;
    }

    public int playerTotal() {
        return playerTotal;
    }

    /** The dealer's total. Only meaningful once the hand is settled. */
    public int dealerTotal() {
        return dealerTotal;
    }

    public boolean playerBust() {
        return playerTotal > TARGET;
    }

    public boolean dealerBust() {
        return dealerTotal > TARGET;
    }

    public List<Draw> playerDraws() {
        return List.copyOf(playerDraws);
    }

    public List<Draw> dealerDraws() {
        return List.copyOf(dealerDraws);
    }

    public Set<Stat> playerUsed() {
        return EnumSet.copyOf(playerUsed.isEmpty() ? EnumSet.noneOf(Stat.class) : playerUsed);
    }
}
