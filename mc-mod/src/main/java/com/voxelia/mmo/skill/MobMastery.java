package com.voxelia.mmo.skill;

import com.mojang.serialization.Codec;
import com.voxelia.mmo.config.VoxeliaConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-mob-type kill counts (the Bestiary). Killing a type many times raises its
 * "mastery tier", granting a small permanent damage + loot bonus vs that type.
 * Stored as a data attachment; persisted and copied on death.
 */
public final class MobMastery {

    public static final Codec<MobMastery> CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(MobMastery::fromMap, m -> m.kills);

    private final Map<String, Integer> kills = new HashMap<>();

    public MobMastery() {}

    public static MobMastery fromMap(Map<String, Integer> map) {
        MobMastery m = new MobMastery();
        m.kills.putAll(map);
        return m;
    }

    public Map<String, Integer> kills() { return kills; }

    public int getKills(String type) { return kills.getOrDefault(type, 0); }

    public void addKill(String type) { kills.merge(type, 1, Integer::sum); }

    /** Mastery tier for a type, capped by config. */
    public int tier(String type) {
        int per = Math.max(1, VoxeliaConfig.masteryKillsPerTier());
        return Math.min(VoxeliaConfig.masteryMaxTier(), getKills(type) / per);
    }
}
