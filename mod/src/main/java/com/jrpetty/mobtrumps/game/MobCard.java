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
     * This card upgraded {@code level} times (0 = base, 1 = holographic, up to
     * the tier's {@link Tier#maxLevel()}). Each upgrade is shaped by what the
     * mob is known for: +2 lands on its speciality (its highest boostable stat
     * — Attack for a Creeper, Farmable for a Chicken) and +1 on each of its
     * next three defining stats, for +5 per level, never more. Stats cap at 10
     * and capped points flow down the same ranking so no boost is wasted. The
     * result is derived only from the card itself, so everyone who reaches a
     * given level gets exactly the same upgraded card.
     */
    public MobCard upgraded(int level) {
        if (level <= 0) {
            return this;
        }
        int[] cur = {health, attack, size, speed, farmable};
        for (int n = 0; n < level; n++) {
            cur = boostOnce(cur);
        }
        return new MobCard(id, displayName, cur[0], cur[1], cur[2], cur[3], cur[4], rarity);
    }

    /** The holographic (level 1) version of this card. */
    public MobCard foilVersion() {
        return upgraded(1);
    }

    /** Apply one +5 speciality-shaped boost to a stat array, capped at 10. */
    private static int[] boostOnce(int[] base) {
        int[] boosted = base.clone();
        // rank boostable stats by current value, ties in enum order
        Stat[] ranked = BOOSTABLE.clone();
        java.util.Arrays.sort(ranked, (a, b) -> Integer.compare(boosted[boostIndex(b)], boosted[boostIndex(a)]));

        int budget = 5;
        // the speciality's +2 goes to the best-ranked stat with room for the
        // FULL +2 (a stat at 9 or 10 passes it down so the +2 always lands)
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
        // speciality, later passes soak up points that capped stats rejected so
        // no boost is wasted while any boostable stat still has room
        int pass = 0;
        while (budget > 0) {
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
            if (!spent && pass > 0) break; // everything with room is capped
            pass++;
        }
        return boosted;
    }

    /** The effective card for a holder: upgraded to their kill level, and at
     *  least level 1 if they hold the holographic. */
    public MobCard effective(boolean foil, int kills) {
        int level = Math.max(foil ? 1 : 0, tier().upgradeLevel(kills));
        return upgraded(level);
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

    /** The themed group this mob belongs to (Farm, Undead, Nether, ...). */
    public Category category() {
        return MobCategories.of(id);
    }

    /** Collector tier derived from spawn rarity (low rarity = rarer card). */
    public Tier tier() {
        if (rarity <= 2) return Tier.LEGENDARY;
        if (rarity <= 4) return Tier.EPIC;
        if (rarity <= 6) return Tier.RARE;
        if (rarity <= 8) return Tier.UNCOMMON;
        return Tier.COMMON;
    }

    /**
     * The stat this card is strongest in — the CPU leads with it. Compared on
     * {@link Stat#strength(int)} so a "lower wins" stat is ranked by how good
     * its number is, not how big (a rarity-1 legendary leads with Rarity).
     */
    public Stat bestStat() {
        Stat best = Stat.HEALTH;
        for (Stat s : Stat.values()) {
            if (s.strength(stat(s)) > best.strength(stat(best))) best = s;
        }
        return best;
    }
}
