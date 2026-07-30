package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.CardCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The mutable half of a physical card: how battered it is, whether it has
 * spent its one free handling, and whether it is currently in a sleeve.
 *
 * <p>The sleeve is a flag on the card rather than a container holding it. That
 * keeps the card a single item through sleeving and unsleeving, which means
 * there is no moment where a nested stack could be duplicated or dropped, and
 * a sleeved card stays playable, displayable and tradeable as itself.
 *
 * @param condition   0-100, starts at 100 and never rises
 * @param handlings   how many times this card has ever entered a hand
 * @param sleeved     protected, so hand entries cost nothing
 */
public record CardWear(int condition, int handlings, boolean sleeved) {

    /**
     * Hand entries that cost nothing at all. Getting a card and looking at it
     * should not damage it — a brand new card stayed Mint for exactly one
     * pickup before, which meant the second time you ever held it it was
     * already down to 95%.
     */
    public static final int FREE_HANDLINGS = 2;

    /**
     * Charged handlings per wear step. Wear lands in whole 5% steps because the
     * condition ladder is built on them, so instead of shaving the step down we
     * make each step take twice as long to arrive.
     */
    public static final int TOUCHES_PER_STEP = 2;

    public static final CardWear PRISTINE = new CardWear(CardCondition.MINT, 0, false);

    public static final Codec<CardWear> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("condition", CardCondition.MINT).forGetter(CardWear::condition),
            Codec.INT.optionalFieldOf("handlings", 0).forGetter(CardWear::handlings),
            Codec.BOOL.optionalFieldOf("sleeved", false).forGetter(CardWear::sleeved)
    ).apply(i, CardWear::new));

    public String label() {
        return CardCondition.label(condition);
    }

    public CardWear withCondition(int next) {
        return new CardWear(CardCondition.clamp(next), handlings, sleeved);
    }

    public CardWear withSleeved(boolean now) {
        return new CardWear(condition, handlings, now);
    }

    /** Free handlings still owed on this card. */
    public int freeLeft() {
        return Math.max(0, FREE_HANDLINGS - handlings);
    }

    /** How many more times it can be picked up before the next 5% goes. */
    public int touchesToNextStep() {
        if (sleeved) {
            return -1;
        }
        int free = freeLeft();
        if (free > 0) {
            return free + TOUCHES_PER_STEP;
        }
        int into = (handlings - FREE_HANDLINGS) % TOUCHES_PER_STEP;
        return TOUCHES_PER_STEP - into;
    }

    /**
     * Apply one hand entry. The first {@link #FREE_HANDLINGS} are free, and
     * after that it takes {@link #TOUCHES_PER_STEP} of them to cost a step. A
     * sleeved card is untouched entirely and does not even count the handling.
     */
    public CardWear handled(int wearPoints) {
        if (sleeved) {
            return this;
        }
        int next = handlings + 1;
        if (next <= FREE_HANDLINGS) {
            return new CardWear(condition, next, false);
        }
        boolean costs = (next - FREE_HANDLINGS) % TOUCHES_PER_STEP == 0;
        int worn = costs ? CardCondition.clamp(condition - Math.max(0, wearPoints)) : condition;
        return new CardWear(worn, next, false);
    }
}
