package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's allocated talent ranks, keyed by the talent's stable id. Stored as a
 * data attachment; persisted and copied on death.
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

    public Map<String, Integer> ranks() { return ranks; }

    public int getRank(Talent talent) {
        return ranks.getOrDefault(talent.id(), 0);
    }

    public void setRank(Talent talent, int rank) {
        ranks.put(talent.id(), Math.max(0, rank));
    }

    /** Points spent across all of a skill's talents. */
    public int spentIn(Skill skill) {
        int sum = 0;
        for (Talent t : Talent.forSkill(skill)) sum += getRank(t);
        return sum;
    }

    public void clear() { ranks.clear(); }

    /** Clear only the ranks belonging to one skill's talents. */
    public void clearSkill(Skill skill) {
        for (Talent t : Talent.forSkill(skill)) ranks.remove(t.id());
    }
}
