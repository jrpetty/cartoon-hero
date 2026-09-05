package com.voxelia.mmo.skill;

import java.util.Arrays;

/**
 * Shared XP curve: total XP to reach level n is {@code 50 * n^1.5}. Levels run to
 * {@link #MAX_LEVEL}; the thresholds are precomputed once and looked up by binary
 * search, because every HUD frame asks for every skill's level.
 */
public final class SkillCurve {
    public static final int MAX_LEVEL = 500;

    /** XP_FOR[n] = total XP required to reach level n (index 0 unused, XP_FOR[1] = 0). */
    private static final int[] XP_FOR = new int[MAX_LEVEL + 1];

    static {
        for (int level = 1; level <= MAX_LEVEL; level++) {
            XP_FOR[level] = level <= 1 ? 0 : (int) Math.round(50.0 * Math.pow(level, 1.5));
        }
    }

    private SkillCurve() {}

    /** Total accumulated XP required to REACH {@code level} (level 1 == 0 xp). */
    public static int xpForLevel(int level) {
        if (level <= 1) return 0;
        if (level >= MAX_LEVEL) return XP_FOR[MAX_LEVEL];
        return XP_FOR[level];
    }

    public static int levelForXp(int xp) {
        if (xp <= 0) return 1;
        if (xp >= XP_FOR[MAX_LEVEL]) return MAX_LEVEL;
        // Largest level whose threshold is <= xp.
        int i = Arrays.binarySearch(XP_FOR, 1, MAX_LEVEL + 1, xp);
        if (i >= 0) return i;
        int insertion = -i - 1;        // first index with threshold > xp
        return Math.max(1, insertion - 1);
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
