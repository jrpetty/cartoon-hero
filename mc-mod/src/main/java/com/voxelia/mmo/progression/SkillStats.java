package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Human-readable description of the live bonus a skill grants at a given level.
 * Mastery talent ranks scale each skill's signature stat, so the figures shown
 * here are the player's real, post-Mastery numbers — the same ones the abilities
 * and attribute modifiers use.
 */
public final class SkillStats {
    private SkillStats() {}

    public static String describe(ServerPlayer player, Skill skill, int level) {
        int lm1 = level - 1;
        double m = TalentLogic.signatureBonus(player, skill);
        double fort = TalentLogic.fortuneBonus(player, skill);
        return switch (skill) {
            case MINING -> String.format(Locale.ROOT, "+%.1f%% break speed, +%.0f%% Fortune on ores%s%s",
                VoxeliaConfig.miningSpeedPerLevel() * lm1 * 100 * m,
                VoxeliaConfig.miningFortunePerLevel() * level * 100 * fort,
                unlocked(VoxeliaConfig.miningHasteLevel(), level) ? ", Haste w/ pickaxe" : "",
                unlocked(VoxeliaConfig.telekinesisLevel(), level) ? ", Telekinesis" : "");
            case FORAGING -> String.format(Locale.ROOT, "+%.1f%% break speed, +%.0f%% Fortune on wood",
                VoxeliaConfig.foragingSpeedPerLevel() * lm1 * 100 * m,
                VoxeliaConfig.foragingFortunePerLevel() * level * 100 * fort);
            case COMBAT -> String.format(Locale.ROOT, "+%.1f attack damage, +%.1f heal per kill",
                VoxeliaConfig.combatDamagePerLevel() * lm1 * m,
                VoxeliaConfig.combatLifeStealPerLevel() * level * TalentLogic.lifestealBonus(player, skill));
            case FARMING -> String.format(Locale.ROOT, "+%.1f max health (%.1f hearts)",
                VoxeliaConfig.farmingHealthPerLevel() * lm1 * m,
                VoxeliaConfig.farmingHealthPerLevel() * lm1 * m / 2.0);
            case ACROBATICS -> String.format(Locale.ROOT, "+%.0f%% jump height, %.0f%% fall reduction",
                VoxeliaConfig.acrobaticsJumpPerLevel() * lm1 / 0.42 * 100 * m,
                Math.min(95, VoxeliaConfig.acrobaticsFallReductionPerLevel() * level * 100
                    * TalentLogic.fallBonus(player, skill)));
            case FISHING -> String.format(Locale.ROOT, "+%.1f luck, %.0f%% treasure (while fishing)",
                VoxeliaConfig.fishingLuckMax() * (level - 1) / 99.0 * m,
                Math.min(VoxeliaConfig.fishingTreasureChanceMax(),
                    level / 200.0 * TalentLogic.treasureBonus(player, skill)) * 100);
            case EXCAVATION -> String.format(Locale.ROOT, "+%.1f%% dig speed, +%.0f%% Fortune on shovel blocks",
                VoxeliaConfig.excavationSpeedPerLevel() * lm1 * 100 * m,
                VoxeliaConfig.excavationFortunePerLevel() * level * 100 * fort);
            case DEFENSE -> String.format(Locale.ROOT, "+%.1f armor, +%.1f toughness, +%.2f kb-resist%s",
                VoxeliaConfig.defenseArmorPerLevel() * lm1 * m,
                VoxeliaConfig.defenseToughnessPerLevel() * lm1 * m,
                VoxeliaConfig.defenseKnockbackResistPerLevel() * lm1 * m,
                unlocked(VoxeliaConfig.lastStandLevel(), level) ? ", Last Stand" : "");
            case COOKING -> unlocked(VoxeliaConfig.cookingWellFedLevel(), level)
                ? "Well Fed active (saturation + regen on eat)"
                : "Well Fed locked (unlocks at level " + VoxeliaConfig.cookingWellFedLevel() + ")";
            case ALCHEMY -> String.format(Locale.ROOT, "+%.0f%% potion duration on drink",
                VoxeliaConfig.alchemyDurationPerLevel() * level * 100 * m);
            case ARCHERY -> String.format(Locale.ROOT, "+%.1f Power Shot damage on fully-drawn arrows",
                VoxeliaConfig.archeryPowerShotPerLevel() * level * m);
        };
    }

    private static boolean unlocked(int unlockLevel, int level) {
        return unlockLevel > 0 && level >= unlockLevel;
    }
}
