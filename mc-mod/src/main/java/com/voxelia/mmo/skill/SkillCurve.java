package com.voxelia.mmo.skill;

/** Shared XP curve. Classic exponential: total XP to reach level n. */
public final class SkillCurve {
    public static final int MAX_LEVEL = 100;

    private SkillCurve() {}

    /** Total accumulated XP required to REACH {@code level} (level 1 == 0 xp). */
    public static int xpForLevel(int level) {
        if (level <= 1) return 0;
        return (int) Math.round(50.0 * Math.pow(level, 1.5));
    }

    public static int levelForXp(int xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    /** XP earned into the current level (the bar's filled amount). */
    public static int xpIntoLevel(int xp) {
        return xp - xpForLevel(levelForXp(xp));
    }

    /** XP span of the current level (the bar's total). 0 at max level. */
    public static int xpToNext(int xp) {
        int level = levelForXp(xp);
        if (level >= MAX_LEVEL) return 0;
        return xpForLevel(level + 1) - xpForLevel(level);
    }
}
