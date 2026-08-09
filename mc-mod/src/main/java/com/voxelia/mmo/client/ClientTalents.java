package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of talent ranks, prestige, + rules, fed by TalentsSyncPayload. */
public final class ClientTalents {
    private ClientTalents() {}

    private static Map<String, Integer> ranks = new HashMap<>();
    private static Map<String, Integer> prestige = new HashMap<>();
    private static int maxRank = 5;
    private static int levelsPerPoint = 8;
    private static int pointsPerPrestige = 1;
    private static int prestigeMax = 3;

    public static void update(Map<String, Integer> newRanks, int newMaxRank, int newLevelsPerPoint,
                              Map<String, Integer> newPrestige, int newPointsPerPrestige, int newPrestigeMax) {
        ranks = new HashMap<>(newRanks);
        prestige = new HashMap<>(newPrestige);
        maxRank = newMaxRank;
        levelsPerPoint = Math.max(1, newLevelsPerPoint);
        pointsPerPrestige = newPointsPerPrestige;
        prestigeMax = newPrestigeMax;
    }

    public static int maxRank() { return maxRank; }

    /** Skill levels needed per talent point (synced from server config). */
    public static int levelsPerPoint() { return levelsPerPoint; }

    public static int prestigeMax() { return prestigeMax; }

    /** Extra talent points each prestige grants (synced from server config). */
    public static int pointsPerPrestige() { return pointsPerPrestige; }

    public static int rank(Talent talent) {
        return ranks.getOrDefault(talent.id(), 0);
    }

    /** How many times this skill has been prestiged. */
    public static int prestige(Skill skill) {
        return prestige.getOrDefault(skill.id(), 0);
    }

    public static int spentIn(Skill skill) {
        int sum = 0;
        for (Talent t : Talent.forSkill(skill)) sum += rank(t);
        return sum;
    }

    public static int available(Skill skill) {
        int level = ClientSkillData.level(skill);
        return level / levelsPerPoint + prestige(skill) * pointsPerPrestige - spentIn(skill);
    }
}
