package com.jrpetty.mobtrumps.game;

/**
 * One collectable mob card. All six stats are on a 0-10 scale.
 * Rarity is spawn likelihood: 10 = everywhere, 1 = legendary.
 */
public record MobCard(String id, String displayName, int health, int attack,
                      int size, int speed, int farmable, int rarity) {

    public int stat(Stat stat) {
        return switch (stat) {
            case HEALTH -> health;
            case ATTACK -> attack;
            case SIZE -> size;
            case SPEED -> speed;
            case FARMABLE -> farmable;
            case RARITY -> rarity;
        };
    }

    /** Stats a holo may boost. Rarity is excluded — it defines the tier. */
    private static final Stat[] BOOSTABLE =
            {Stat.HEALTH, Stat.ATTACK, Stat.SIZE, Stat.SPEED, Stat.FARMABLE};

    /**
     * The holographic version of this card. Each card's holo boost is shaped
     * by what that mob is known for: +2 lands on its speciality (its highest
     * boostable stat — Attack for a Creeper, Farmable for a Chicken, Speed
     * for a Horse) and +1 on each of its next three defining stats, for +5
     * total, never more. Stats cap at 10; capped points flow down the same
     * ranking so no card wastes its boost. The ranking is derived only from
     * the card itself, so everyone who unlocks a holo gets exactly the same
     * upgraded card.
     */
    public MobCard foilVersion() {
        // rank boostable stats by this card's values, ties in enum order
        Stat[] ranked = BOOSTABLE.clone();
        java.util.Arrays.sort(ranked, (a, b) -> Integer.compare(stat(b), stat(a)));

        int[] boosted = {health, attack, size, speed, farmable};
        int budget = 5;

        // the speciality's +2 goes to the best-ranked stat with room for the
        // FULL +2 (a stat sitting at 9 or 10 passes it down the ranking, so
        // every holo lands its promised +2 somewhere it counts)
        int specIdx = -1;
        for (Stat s : ranked) {
            int i = boostIndex(s);
            if (boosted[i] <= 8) {
                boosted[i] += 2;
                budget -= 2;
                specIdx = i;
                break;
            }
        }
        if (specIdx < 0) {
            // freak case: every boostable stat is 9+ — take what fits
            for (Stat s : ranked) {
                int i = boostIndex(s);
                int add = Math.min(2, 10 - boosted[i]);
                if (add > 0) {
                    boosted[i] += add;
                    budget -= add;
                    specIdx = i;
                    break;
                }
            }
        }
        // spread the rest as +1s down the ranking; the first pass skips the
        // speciality, later passes soak up points that capped stats rejected
        int pass = 0;
        while (budget > 0 && pass < 3) {
            boolean spent = false;
            for (Stat s : ranked) {
                if (budget <= 0) break;
                int i = boostIndex(s);
                if (pass == 0 && i == specIdx) continue;
                if (boosted[i] < 10) {
                    boosted[i]++;
                    budget--;
                    spent = true;
                }
            }
            if (!spent && pass > 0) break; // everything is capped
            pass++;
        }

        return new MobCard(id, displayName,
                boosted[0], boosted[1], boosted[2], boosted[3], boosted[4], rarity);
    }

    private static int boostIndex(Stat stat) {
        return switch (stat) {
            case HEALTH -> 0;
            case ATTACK -> 1;
            case SIZE -> 2;
            case SPEED -> 3;
            case FARMABLE -> 4;
            case RARITY -> throw new IllegalArgumentException("rarity is not boostable");
        };
    }

    /** Collector tier derived from spawn rarity (low rarity = rarer card). */
    public Tier tier() {
        if (rarity <= 2) return Tier.LEGENDARY;
        if (rarity <= 4) return Tier.EPIC;
        if (rarity <= 6) return Tier.RARE;
        if (rarity <= 8) return Tier.UNCOMMON;
        return Tier.COMMON;
    }

    /** The stat this card is strongest in — the CPU leads with it. */
    public Stat bestStat() {
        Stat best = Stat.HEALTH;
        for (Stat s : Stat.values()) {
            if (stat(s) > stat(best)) best = s;
        }
        return best;
    }
}
