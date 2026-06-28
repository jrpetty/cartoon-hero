package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's allocated talent ranks, keyed by "<skillId>.<talentId>". Stored as
 * a data attachment; persisted and copied on death.
 */
public final class PlayerTalents {

    public static final Codec<PlayerTalents> CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(PlayerTalents::fromMap, m -> m.ranks);

    private final Map<String, Integer> ranks = new HashMap<>();

    public PlayerTalents() {}

    public static PlayerTalents fromMap(Map<String, Integer> map) {
        PlayerTalents t = new PlayerTalents();
        t.ranks.putAll(map);
        return t;
    }

    public static String key(Skill skill, TalentType type) {
        return skill.id() + "." + type.id();
    }

    public Map<String, Integer> ranks() { return ranks; }

    public int getRank(Skill skill, TalentType type) {
        return ranks.getOrDefault(key(skill, type), 0);
    }

    public void setRank(Skill skill, TalentType type, int rank) {
        ranks.put(key(skill, type), Math.max(0, rank));
    }

    public int spentIn(Skill skill) {
        int sum = 0;
        for (TalentType t : TalentType.values()) sum += getRank(skill, t);
        return sum;
    }

    public void clear() { ranks.clear(); }
}
