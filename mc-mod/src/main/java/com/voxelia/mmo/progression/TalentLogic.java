package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.TalentType;
import net.minecraft.server.level.ServerPlayer;

/** Talent points, spending, and the multipliers the rest of the mod reads. */
public final class TalentLogic {
    private TalentLogic() {}

    public static int rank(ServerPlayer p, Skill skill, TalentType type) {
        return p.getData(VoxeliaAttachments.PLAYER_TALENTS.get()).getRank(skill, type);
    }

    /** Points earned (per skill level) minus points already spent in that skill. */
    public static int pointsAvailable(ServerPlayer p, Skill skill) {
        int level = p.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill);
        int earned = Math.max(0, level - 1) * VoxeliaConfig.talentPointsPerLevel();
        int spent = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get()).spentIn(skill);
        return earned - spent;
    }

    /** Try to spend one point into a talent. Returns true on success. */
    public static boolean spend(ServerPlayer p, Skill skill, TalentType type) {
        PlayerTalents talents = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        if (talents.getRank(skill, type) >= VoxeliaConfig.talentMaxRank()) return false;
        if (pointsAvailable(p, skill) <= 0) return false;
        talents.setRank(skill, type, talents.getRank(skill, type) + 1);
        p.setData(VoxeliaAttachments.PLAYER_TALENTS.get(), talents);
        return true;
    }

    public static void reset(ServerPlayer p) {
        PlayerTalents talents = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        talents.clear();
        p.setData(VoxeliaAttachments.PLAYER_TALENTS.get(), talents);
    }

    /** XP gain multiplier for a skill from its Prodigy ranks. */
    public static double prodigyMultiplier(ServerPlayer p, Skill skill) {
        return 1.0 + rank(p, skill, TalentType.PRODIGY) * VoxeliaConfig.prodigyXpPerRank();
    }

    /** Effective Mastery rank (× global scale) used to size attribute bonuses. */
    public static double mastery(ServerPlayer p, Skill skill) {
        return rank(p, skill, TalentType.MASTERY) * VoxeliaConfig.talentMasteryScale();
    }
}
