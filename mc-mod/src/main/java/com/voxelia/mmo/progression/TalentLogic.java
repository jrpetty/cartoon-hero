package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.server.level.ServerPlayer;

/** Talent points, spending, and the per-category multipliers the rest of the mod reads. */
public final class TalentLogic {
    private TalentLogic() {}

    public static int rank(ServerPlayer p, Talent talent) {
        return p.getData(VoxeliaAttachments.PLAYER_TALENTS.get()).getRank(talent);
    }

    /** Points earned (1 per N skill levels) minus points already spent in that skill. */
    public static int pointsAvailable(ServerPlayer p, Skill skill) {
        int level = p.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill);
        int earned = level / VoxeliaConfig.talentLevelsPerPoint();
        int spent = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get()).spentIn(skill);
        return earned - spent;
    }

    /** Try to spend one point into a talent. Returns true on success. */
    public static boolean spend(ServerPlayer p, Talent talent) {
        PlayerTalents talents = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        if (talents.getRank(talent) >= VoxeliaConfig.talentMaxRank()) return false;
        if (pointsAvailable(p, talent.skill()) <= 0) return false;
        talents.setRank(talent, talents.getRank(talent) + 1);
        p.setData(VoxeliaAttachments.PLAYER_TALENTS.get(), talents);
        return true;
    }

    public static void reset(ServerPlayer p) {
        PlayerTalents talents = p.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        talents.clear();
        p.setData(VoxeliaAttachments.PLAYER_TALENTS.get(), talents);
    }

    /** Multiplier (1.0 = none) from the skill's talent in the given category. */
    public static double bonus(ServerPlayer p, Skill skill, Talent.Category category) {
        Talent t = Talent.of(skill, category);
        return t == null ? 1.0 : t.multiplierAt(rank(p, t));
    }

    public static double xpBonus(ServerPlayer p, Skill skill)        { return bonus(p, skill, Talent.Category.XP); }
    public static double signatureBonus(ServerPlayer p, Skill skill) { return bonus(p, skill, Talent.Category.SIGNATURE); }
    public static double fortuneBonus(ServerPlayer p, Skill skill)   { return bonus(p, skill, Talent.Category.FORTUNE); }
    public static double lifestealBonus(ServerPlayer p, Skill skill) { return bonus(p, skill, Talent.Category.LIFESTEAL); }
    public static double fallBonus(ServerPlayer p, Skill skill)      { return bonus(p, skill, Talent.Category.FALL); }
    public static double treasureBonus(ServerPlayer p, Skill skill)  { return bonus(p, skill, Talent.Category.TREASURE); }
}
