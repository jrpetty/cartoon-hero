package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

/**
 * How many times a player has prestiged each skill, keyed by skill id. Each
 * prestige resets the skill to level 1 (and its talents) but grants a permanent
 * extra talent point in that skill. Stored as a data attachment; persisted and
 * copied on death.
 */
public final class PlayerPrestige {

    public static final Codec<PlayerPrestige> CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(PlayerPrestige::fromMap, p -> p.counts);

    private final Map<String, Integer> counts = new HashMap<>();

    public PlayerPrestige() {}

    public static PlayerPrestige fromMap(Map<String, Integer> map) {
        PlayerPrestige p = new PlayerPrestige();
        p.counts.putAll(map);
        return p;
    }

    public Map<String, Integer> counts() { return counts; }

    public int get(Skill skill) { return counts.getOrDefault(skill.id(), 0); }

    public void set(Skill skill, int value) { counts.put(skill.id(), Math.max(0, value)); }

    public int total() {
        int sum = 0;
        for (int v : counts.values()) sum += v;
        return sum;
    }
}
