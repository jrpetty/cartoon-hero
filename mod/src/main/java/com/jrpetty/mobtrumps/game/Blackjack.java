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
 * <p><b>A stat may be called as often as you like.</b> That hands the player a
 * large advantage on its own — you can simply keep calling the safest stat — so
 * the dealer is set against it rather than against a casino's rules: it draws
 * until {@value #DEALER_STANDS} and takes ties. Measured, that brings the game
 * back to roughly even, at the cost of a dealer that busts about half the time.
 * A loud table, but a fair one.
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
     * What may be wagered on a hand. A win pays the stake back and matches it.
     *
     * <p>A ladder rather than a free number: the odds do not shift with the size
     * of the bet, only the variance does, so the real choice is how big a swing
     * you want. Doubling each rung keeps that legible. Lives here, with the
     * rules, so the screen and the table read it from the same place.
     */
    public static final int[] STAKES = {2, 4, 8, 16, 32, 64};

    /** Where a player starts on the ladder. */
    public static final int DEFAULT_STAKE = 2;

    /** A legal rung, whatever a client asks for. */
    public static int clampStakeIndex(int index) {
        return Math.max(0, Math.min(STAKES.length - 1, index));
    }

    /**
     * The dealer draws below this. Far above a casino's 17 because a player who
     * may repeat a stat can nurse a hand upward almost without risk: measured,
     * a dealer standing on 17 leaves the player a 36% edge, on 19 a 9% one. At
     * 20 the game is within about a point of even. The dealer busts roughly half
     * the time as a result, which is the price of letting stats repeat.
     */
    public static final int DEALER_STANDS = 20;

    /**
     * A hand cannot run longer than this. Attack is 0 on thirty of the eighty-one
     * mobs, so with repeats allowed a total can sit still indefinitely; without a
     * ceiling a hand has no guaranteed end and the table would grow without bound.
     */
    public static final int MAX_DRAWS = 12;

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

    /** Every stat, always — a call may be repeated as often as you like. */
    public Set<Stat> availableStats() {
        return EnumSet.allOf(Stat.class);
    }

    public boolean canHit() {
        return phase == Phase.PLAYER && playerDraws.size() < MAX_DRAWS;
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
        if (stat == null) {
            throw new IllegalArgumentException("a stat must be called");
        }
        if (playerDraws.size() >= MAX_DRAWS) {
            throw new IllegalStateException("this hand has run its full length");
        }
        MobCard card = deal();
        int value = card.stat(stat);
        playerTotal += value;
        Draw draw = new Draw(stat, card.id(), value, playerTotal);
        playerDraws.add(draw);
        if (playerTotal > TARGET) {
            phase = Phase.DONE;
            result = Result.DEALER_WIN;
        } else if (playerDraws.size() >= MAX_DRAWS) {
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
        if (dealerDraws.size() >= MAX_DRAWS) {
            return null;
        }
        int need = TARGET - dealerTotal;
        Stat best = null;
        double bestGap = Double.MAX_VALUE;
        for (Stat stat : Stat.values()) {
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
        } else {
            // the house takes ties. With repeats allowed the player can nurse a
            // hand up almost risklessly, and pushing on a tie handed them a 9%
            // edge on top of that; this is the cheapest way to claw it back.
            result = Result.DEALER_WIN;
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

    /** How many cards the player has taken, against {@link #MAX_DRAWS}. */
    public int drawsTaken() {
        return playerDraws.size();
    }
}
