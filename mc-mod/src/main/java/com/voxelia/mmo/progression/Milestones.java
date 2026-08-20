package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * The level-gated perks — every one of which used to unlock in silence. A skill's
 * signature ability arrives somewhere between level 15 and 30, and four passives
 * (Haste, Telekinesis, Last Stand, Well Fed) land on their own thresholds.
 *
 * <p>Levels are read from config on each call, so a server that retunes them gets
 * the announcements at the retuned levels. The ability gates mirror the ones
 * {@link Abilities} enforces when you press the key.
 */
public final class Milestones {
    private Milestones() {}

    /** What unlocked. The client turns this into a name and a one-line explanation. */
    public enum Kind { ABILITY, HASTE, TELEKINESIS, LAST_STAND, WELL_FED }

    /** The level at which this skill's signature ability unlocks (0 = disabled). */
    public static int abilityLevel(Skill skill) {
        return switch (skill) {
            case MINING -> VoxeliaConfig.minersFocusLevel();
            case FORAGING -> VoxeliaConfig.overgrowthLevel();
            case COMBAT -> VoxeliaConfig.frenzyLevel();
            case FARMING -> VoxeliaConfig.heartyMealLevel();
            case ACROBATICS -> VoxeliaConfig.leapLevel();
            case FISHING -> VoxeliaConfig.maelstromLevel();
            case EXCAVATION -> VoxeliaConfig.excavateLevel();
            case DEFENSE -> VoxeliaConfig.bulwarkLevel();
            case COOKING -> VoxeliaConfig.feastLevel();
            case ALCHEMY -> VoxeliaConfig.panaceaLevel();
            case ARCHERY -> VoxeliaConfig.volleyLevel();
        };
    }

    /** Everything this skill unlocks exactly at {@code level} — usually nothing. */
    public static List<Kind> at(Skill skill, int level) {
        List<Kind> out = new ArrayList<>(2);
        if (level > 0 && abilityLevel(skill) == level) out.add(Kind.ABILITY);
        switch (skill) {
            case MINING -> {
                if (VoxeliaConfig.miningHasteLevel() == level) out.add(Kind.HASTE);
                if (VoxeliaConfig.telekinesisLevel() == level) out.add(Kind.TELEKINESIS);
            }
            case DEFENSE -> {
                if (VoxeliaConfig.lastStandLevel() == level) out.add(Kind.LAST_STAND);
            }
            case COOKING -> {
                if (VoxeliaConfig.cookingWellFedLevel() == level) out.add(Kind.WELL_FED);
            }
            default -> { }
        }
        return out;
    }

    /** The level a passive perk unlocks at, for "unlocks at Lv N" hints. */
    public static int levelOf(Skill skill, Kind kind) {
        return switch (kind) {
            case ABILITY -> abilityLevel(skill);
            case HASTE -> VoxeliaConfig.miningHasteLevel();
            case TELEKINESIS -> VoxeliaConfig.telekinesisLevel();
            case LAST_STAND -> VoxeliaConfig.lastStandLevel();
            case WELL_FED -> VoxeliaConfig.cookingWellFedLevel();
        };
    }
}
