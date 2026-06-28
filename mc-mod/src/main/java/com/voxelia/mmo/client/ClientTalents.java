package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.TalentType;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of talent ranks + rules, fed by TalentsSyncPayload. */
public final class ClientTalents {
    private ClientTalents() {}

    private static Map<String, Integer> ranks = new HashMap<>();
    private static int maxRank = 5;
    private static int levelsPerPoint = 8;

    public static void update(Map<String, Integer> newRanks, int newMaxRank, int newLevelsPerPoint) {
        ranks = new HashMap<>(newRanks);
        maxRank = newMaxRank;
        levelsPerPoint = Math.max(1, newLevelsPerPoint);
    }

    public static int maxRank() { return maxRank; }

    public static int rank(Skill skill, TalentType type) {
        return ranks.getOrDefault(PlayerTalents.key(skill, type), 0);
    }

    public static int spentIn(Skill skill) {
        int sum = 0;
        for (TalentType t : TalentType.values()) sum += rank(skill, t);
        return sum;
    }

    public static int available(Skill skill) {
        int level = ClientSkillData.level(skill);
        return level / levelsPerPoint - spentIn(skill);
    }
}
