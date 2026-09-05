package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of talent ranks + rules, fed by TalentsSyncPayload. */
public final class ClientTalents {
    private ClientTalents() {}

    private static Map<String, Integer> ranks = new HashMap<>();
    private static int maxRank = 5;
    private static int levelsPerPoint = 20;

    public static void update(Map<String, Integer> newRanks, int newMaxRank, int newLevelsPerPoint) {
        ranks = new HashMap<>(newRanks);
        maxRank = newMaxRank;
        levelsPerPoint = Math.max(1, newLevelsPerPoint);
    }

    public static int maxRank() { return maxRank; }

    /** Skill levels needed per talent point (synced from the server). */
    public static int levelsPerPoint() { return levelsPerPoint; }

    public static int rank(Talent talent) {
        return ranks.getOrDefault(talent.id(), 0);
    }

    public static int spentIn(Skill skill) {
        int sum = 0;
        for (Talent t : Talent.forSkill(skill)) sum += rank(t);
        return sum;
    }

    public static int available(Skill skill) {
        return ClientSkillData.level(skill) / levelsPerPoint - spentIn(skill);
    }
}
