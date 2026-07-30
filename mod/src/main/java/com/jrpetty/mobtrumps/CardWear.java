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
 * @param condition      0-100, starts at 100 and never rises
 * @param firstHandUsed  the one free hand entry, once per card ever
 * @param sleeved        protected, so hand entries cost nothing
 */
public record CardWear(int condition, boolean firstHandUsed, boolean sleeved) {

    public static final CardWear PRISTINE = new CardWear(CardCondition.MINT, false, false);

    public static final Codec<CardWear> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("condition", CardCondition.MINT).forGetter(CardWear::condition),
            Codec.BOOL.optionalFieldOf("first_hand_used", false).forGetter(CardWear::firstHandUsed),
            Codec.BOOL.optionalFieldOf("sleeved", false).forGetter(CardWear::sleeved)
    ).apply(i, CardWear::new));

    public String label() {
        return CardCondition.label(condition);
    }

    public CardWear withCondition(int next) {
        return new CardWear(CardCondition.clamp(next), firstHandUsed, sleeved);
    }

    public CardWear withFirstHandUsed() {
        return new CardWear(condition, true, sleeved);
    }

    public CardWear withSleeved(boolean now) {
        return new CardWear(condition, firstHandUsed, now);
    }

    /**
     * Apply one qualifying hand entry. The first ever is free and only burns the
     * exemption; a sleeved card is untouched entirely.
     */
    public CardWear handled(int wearPoints) {
        if (sleeved) {
            return this;
        }
        if (!firstHandUsed) {
            return withFirstHandUsed();
        }
        return withCondition(condition - Math.max(0, wearPoints));
    }
}
