package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerPrestige;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.server.level.ServerPlayer;

/** Prestiging a maxed skill: reset it to level 1 (and its talents) for a permanent extra talent point. */
public final class PrestigeLogic {
    private PrestigeLogic() {}

    public static int count(ServerPlayer p, Skill skill) {
        return p.getData(VoxeliaAttachments.PLAYER_PRESTIGE.get()).get(skill);
    }

    /** Extra talent points a skill has from prestiging (given immediately, even at level 1). */
    public static int bonusPoints(ServerPlayer p, Skill skill) {
        return count(p, skill) * VoxeliaConfig.prestigePointsPerPrestige();
    }

    public static boolean canPrestige(ServerPlayer p, Skill skill) {
        if (!VoxeliaConfig.prestigeEnabled()) return false;
        if (p.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill) < SkillCurve.MAX_LEVEL) return false;
        return count(p, skill) < VoxeliaConfig.prestigeMax();
    }

    /**
     * Prestige a skill. Returns true on success. Resets the skill to level 1, clears that
     * skill's talent ranks, and increments the prestige count (which adds a talent point).
     */
    public static boolean prestige(ServerPlayer p, Skill skill) {
        if (!canPrestige(p, skill)) return false;

        PlayerSkills skills = p.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        skills.resetSkill(skill);
        p.setData(VoxeliaAttachments.PLAYER_SKILLS.get(), skills);

        PlayerTalents talents = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        talents.clearSkill(skill);
        p.setData(VoxeliaAttachments.PLAYER_TALENTS.get(), talents);

        PlayerPrestige prestige = p.getData(VoxeliaAttachments.PLAYER_PRESTIGE.get());
        prestige.set(skill, prestige.get(skill) + 1);
        p.setData(VoxeliaAttachments.PLAYER_PRESTIGE.get(), prestige);
        return true;
    }
}
