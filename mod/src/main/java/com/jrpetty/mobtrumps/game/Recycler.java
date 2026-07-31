package com.jrpetty.mobtrumps.game;

/**
 * The recycler's arithmetic: pulp spare cards into fragments, spend fragments
 * to print new ones.
 *
 * <p><b>Yield scales with tier, gently.</b> The instinct is to pay 1:20 for a
 * legendary, but the drop ladder already equalises effort — a common takes
 * about twenty kills and a legendary takes one — so in <em>work</em> terms they
 * cost about the same. A mild curve matches what a player expects without
 * turning boss farming into a fragment mine.
 *
 * <p><b>Odds are linear in what you invest, and a failure keeps nothing.</b>
 * That is the property the whole thing rests on: 15 fragments at 50% averages
 * 30 per common, and so does 30 fragments at 100%. The expected cost is
 * identical however you play, so there is no optimal strategy to look up — only
 * a choice about variance. Add a consolation refund and it collapses: refund a
 * quarter and expected cost becomes {@code 0.75·max + 0.25·invested}, investing
 * low is strictly cheaper, everyone minimum-bets and the decision evaporates.
 *
 * <p>A print yields a <b>random card of that tier</b>, not necessarily one you
 * are missing. The press is for volume and trading; hunting is for completion.
 */
public final class Recycler {

    /** Below this a stake is not worth the click, and clogs the UI. */
    public static final int MIN_STAKE = 5;

    private Recycler() {
    }

    /** Fragments a mint card of this tier is worth. */
    public static int baseYield(Tier tier) {
        return switch (tier) {
            case COMMON -> 4;
            case UNCOMMON -> 6;
            case RARE -> 10;
            case EPIC -> 16;
            case LEGENDARY -> 28;
        };
    }

    /** Fragments that guarantee a print of this tier. */
    public static int maxStake(Tier tier) {
        return switch (tier) {
            case COMMON -> 30;
            case UNCOMMON -> 45;
            case RARE -> 70;
            case EPIC -> 110;
            case LEGENDARY -> 180;
        };
    }

    /**
     * What the shredder actually pays for one card.
     *
     * <p>Condition counts: a mint spare pays the full yield and a ruined one
     * pays half, so a battered duplicate is the natural thing to pulp and a
     * pristine one is worth keeping. Never less than one fragment — a ruined
     * card is still cardboard.
     */
    public static int yield(Tier tier, int condition) {
        int c = Math.max(0, Math.min(100, condition));
        int base = baseYield(tier);
        return Math.max(1, Math.round(base * (0.5f + 0.5f * c / 100f)));
    }

    /** Chance 0-1 that a stake prints a card. Linear, capped at the max. */
    public static float chance(Tier tier, int stake) {
        int max = maxStake(tier);
        if (stake >= max) {
            return 1f;
        }
        return Math.max(0f, stake / (float) max);
    }

    /** The same as a whole percentage, for the UI. */
    public static int percent(Tier tier, int stake) {
        return Math.round(chance(tier, stake) * 100f);
    }

    /** A legal stake for this tier: at least the minimum, never over the max. */
    public static int clampStake(Tier tier, int stake) {
        return Math.max(MIN_STAKE, Math.min(maxStake(tier), stake));
    }

    /**
     * Expected fragments spent per card printed. Constant in the stake by
     * construction — if this ever varies, the no-refund rule has been broken.
     */
    public static float expectedCost(Tier tier, int stake) {
        float p = chance(tier, stake);
        return p <= 0f ? Float.POSITIVE_INFINITY : stake / p;
    }
}
