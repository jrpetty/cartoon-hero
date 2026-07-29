package com.jrpetty.mobtrumps.game;

import java.util.Locale;

/** Collector tier of a card, derived from how rarely the mob spawns. */
public enum Tier {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY;

    public String label() {
        String n = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /**
     * Kill milestones that upgrade this mob's card. Reaching the first unlocks
     * the holographic (upgrade level 1); each later milestone bumps the level
     * again (up to 3), stacking another stat boost. Rarer mobs are harder to
     * find, so they need fewer kills — but every rarity follows the same
     * 1x / 2.5x / 5x curve as common's 100 / 250 / 500.
     */
    public int[] milestones() {
        return switch (this) {
            case COMMON -> new int[]{100, 250, 500};
            case UNCOMMON -> new int[]{75, 190, 375};
            case RARE -> new int[]{25, 65, 125};
            case EPIC -> new int[]{10, 25, 50};
            case LEGENDARY -> new int[]{5, 15, 25};
        };
    }

    /** How many milestones {@code kills} has passed: the card's upgrade level (0-3). */
    public int upgradeLevel(int kills) {
        int level = 0;
        for (int m : milestones()) {
            if (kills >= m) level++;
        }
        return level;
    }

    public int maxLevel() {
        return milestones().length;
    }

    /**
     * Chance that killing this mob drops its card. Commons are 1 in 20 and each
     * step up the ladder roughly doubles it, up to a guaranteed legendary.
     *
     * <p>The ladder is deliberately hung on TIER rather than on the themed
     * category, because a category mixes rarities: Farm Animals holds both the
     * chicken you kill by the hundred and the sniffer you may meet once. Tier is
     * derived from spawn rarity, so it already knows how often you will get the
     * chance — which is the thing a drop rate has to compensate for. It also
     * makes every boss a certain drop without a special case, since the Ender
     * Dragon, the Wither and the Warden are all rarity 1.
     */
    public float cardDropChance() {
        return switch (this) {
            case COMMON -> 0.05f;     // 1 in 20
            case UNCOMMON -> 0.10f;   // 1 in 10
            case RARE -> 0.20f;       // 1 in 5
            case EPIC -> 0.50f;       // 1 in 2
            case LEGENDARY -> 1.00f;  // guaranteed
        };
    }

    /**
     * Kills of one mob without its card after which the next one is guaranteed.
     * Set at three times the average wait, so it never shortens a normal hunt —
     * it only cuts off the miserable tail where the dice simply refuse.
     */
    public int pityKills() {
        return switch (this) {
            case COMMON -> 60;
            case UNCOMMON -> 30;
            case RARE -> 15;
            case EPIC -> 6;
            case LEGENDARY -> 1;
        };
    }

    /** Kills needed to unlock the holographic (the first milestone). */
    public int foilKillThreshold() {
        return milestones()[0];
    }

    /** The next milestone above {@code kills}, or -1 if already maxed. */
    public int nextMilestone(int kills) {
        for (int m : milestones()) {
            if (kills < m) return m;
        }
        return -1;
    }
}
