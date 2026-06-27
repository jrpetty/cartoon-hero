package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.skill.Skill;

import java.util.Locale;

/** Human-readable description of the live bonus a skill grants at a given level. */
public final class SkillStats {
    private SkillStats() {}

    public static String describe(Skill skill, int level) {
        int lm1 = level - 1;
        return switch (skill) {
            case MINING -> String.format(Locale.ROOT, "+%.1f%% break speed, +%.0f%% Fortune on ores%s%s",
                VoxeliaConfig.miningSpeedPerLevel() * lm1 * 100,
                VoxeliaConfig.miningFortunePerLevel() * level * 100,
                unlocked(VoxeliaConfig.miningHasteLevel(), level) ? ", Haste w/ pickaxe" : "",
                unlocked(VoxeliaConfig.telekinesisLevel(), level) ? ", Telekinesis" : "");
            case FORAGING -> String.format(Locale.ROOT, "+%.1f%% break speed, +%.0f%% Fortune on wood",
                VoxeliaConfig.foragingSpeedPerLevel() * lm1 * 100,
                VoxeliaConfig.foragingFortunePerLevel() * level * 100);
            case COMBAT -> String.format(Locale.ROOT, "+%.1f attack damage, +%.1f heal per kill",
                VoxeliaConfig.combatDamagePerLevel() * lm1,
                VoxeliaConfig.combatLifeStealPerLevel() * level);
            case FARMING -> String.format(Locale.ROOT, "+%.1f max health (%.1f hearts)",
                VoxeliaConfig.farmingHealthPerLevel() * lm1,
                VoxeliaConfig.farmingHealthPerLevel() * lm1 / 2.0);
            case ACROBATICS -> String.format(Locale.ROOT, "%.0f%% dodge, %.0f%% fall reduction",
                Math.min(95, VoxeliaConfig.acrobaticsDodgePerLevel() * level * 100),
                Math.min(95, VoxeliaConfig.acrobaticsFallReductionPerLevel() * level * 100));
            case FISHING -> String.format(Locale.ROOT, "+%.1f luck, up to %.1fx bite speed, %.0f%% treasure (while fishing)",
                VoxeliaConfig.fishingLuckMax() * (level - 1) / 99.0,
                VoxeliaConfig.fishingSpeedMax(),
                Math.min(VoxeliaConfig.fishingTreasureChanceMax(), level / 200.0) * 100);
            case EXCAVATION -> String.format(Locale.ROOT, "+%.1f%% dig speed, +%.0f%% Fortune on shovel blocks",
                VoxeliaConfig.excavationSpeedPerLevel() * lm1 * 100,
                VoxeliaConfig.excavationFortunePerLevel() * level * 100);
            case DEFENSE -> String.format(Locale.ROOT, "+%.1f armor, +%.1f toughness, +%.2f kb-resist%s",
                VoxeliaConfig.defenseArmorPerLevel() * lm1,
                VoxeliaConfig.defenseToughnessPerLevel() * lm1,
                VoxeliaConfig.defenseKnockbackResistPerLevel() * lm1,
                unlocked(VoxeliaConfig.lastStandLevel(), level) ? ", Last Stand" : "");
            case COOKING -> unlocked(VoxeliaConfig.cookingWellFedLevel(), level)
                ? "Well Fed active (saturation + regen on eat)"
                : "Well Fed locked (unlocks at level " + VoxeliaConfig.cookingWellFedLevel() + ")";
            case ALCHEMY -> String.format(Locale.ROOT, "+%.0f%% potion duration on drink",
                VoxeliaConfig.alchemyDurationPerLevel() * level * 100);
            case ARCHERY -> String.format(Locale.ROOT, "+%.1f Power Shot damage on fully-drawn arrows",
                VoxeliaConfig.archeryPowerShotPerLevel() * level);
        };
    }

    private static boolean unlocked(int unlockLevel, int level) {
        return unlockLevel > 0 && level >= unlockLevel;
    }
}
