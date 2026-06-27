package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A player's skill XP. Stored as a data attachment (see VoxeliaAttachments),
 * serialized with {@link #CODEC}, persisted to the save, and copied on death.
 */
public final class PlayerSkills {

    /** Persisted form: a map of skill -> raw XP. Missing skills default to 0. */
    public static final Codec<PlayerSkills> CODEC =
        Codec.unboundedMap(Skill.CODEC, Codec.INT).xmap(PlayerSkills::fromMap, PlayerSkills::toMap);

    private final EnumMap<Skill, Integer> xp = new EnumMap<>(Skill.class);

    public PlayerSkills() {
        for (Skill s : Skill.values()) xp.put(s, 0);
    }

    public static PlayerSkills fromMap(Map<Skill, Integer> map) {
        PlayerSkills ps = new PlayerSkills();
        map.forEach((k, v) -> ps.xp.put(k, v == null ? 0 : v));
        return ps;
    }

    public Map<Skill, Integer> toMap() {
        return new HashMap<>(xp);
    }

    public int getXp(Skill s) { return xp.getOrDefault(s, 0); }

    public int getLevel(Skill s) { return SkillCurve.levelForXp(getXp(s)); }

    public void addXp(Skill s, int amount) {
        xp.merge(s, Math.max(0, amount), Integer::sum);
    }

    /** Reduce every skill's XP by a fraction (death penalty). pct in [0,1]. */
    public void loseFraction(double pct) {
        double keep = Math.max(0.0, 1.0 - pct);
        for (Skill s : Skill.values()) {
            xp.put(s, (int) Math.floor(getXp(s) * keep));
        }
    }

    public int totalLevels() {
        int sum = 0;
        for (Skill s : Skill.values()) sum += getLevel(s);
        return sum;
    }

    /** Headline character level = average skill level (min 1). */
    public int characterLevel() {
        return Math.max(1, Math.round(totalLevels() / (float) Skill.values().length));
    }
}
