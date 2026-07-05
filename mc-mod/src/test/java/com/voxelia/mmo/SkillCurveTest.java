package com.voxelia.mmo;

import com.voxelia.mmo.skill.SkillCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The XP curve is pure integer math with no Minecraft dependencies. */
class SkillCurveTest {

    @Test
    void levelOneAtZeroXp() {
        assertEquals(0, SkillCurve.xpForLevel(1), "level 1 requires 0 xp");
        assertEquals(1, SkillCurve.levelForXp(0), "0 xp is level 1");
        assertEquals(1, SkillCurve.levelForXp(-5), "negative xp clamps to level 1");
    }

    @Test
    void levelForXpMatchesThresholds() {
        for (int level = 2; level <= SkillCurve.MAX_LEVEL; level++) {
            int need = SkillCurve.xpForLevel(level);
            assertEquals(level, SkillCurve.levelForXp(need),
                "exactly xpForLevel(" + level + ") should be level " + level);
            assertEquals(level - 1, SkillCurve.levelForXp(need - 1),
                "one xp short of level " + level + " should still be level " + (level - 1));
        }
    }

    @Test
    void levelIsMonotonicAndCapped() {
        int prev = 1;
        for (int xp = 0; xp < 5_000_000; xp += 811) {
            int lvl = SkillCurve.levelForXp(xp);
            assertTrue(lvl >= prev, "level must never decrease as xp grows");
            assertTrue(lvl <= SkillCurve.MAX_LEVEL, "level must never exceed the cap");
            prev = lvl;
        }
        assertEquals(SkillCurve.MAX_LEVEL, SkillCurve.levelForXp(Integer.MAX_VALUE), "huge xp caps at max level");
    }

    @Test
    void progressBarPartsAreConsistent() {
        for (int xp = 0; xp < 2_000_000; xp += 1237) {
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            assertEquals(xp, SkillCurve.xpForLevel(level) + into, "into-level + base == total xp");
            if (level < SkillCurve.MAX_LEVEL) {
                assertTrue(span > 0, "a non-max level has a positive span");
                assertTrue(into >= 0 && into < span, "progress into the level stays within its span");
            } else {
                assertEquals(0, span, "max level has no next-level span");
            }
        }
    }
}
