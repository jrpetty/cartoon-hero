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
