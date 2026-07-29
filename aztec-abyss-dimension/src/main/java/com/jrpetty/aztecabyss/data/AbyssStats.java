package com.jrpetty.aztecabyss.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-persistent leaderboard for the Abyss. Solo and co-op runs are tracked
 * separately (best round + longest survival for each), plus lifetime kills,
 * revives and clears. Surfaced through {@code /abyss stats|leaderboard}.
 */
public final class AbyssStats extends SavedData {

    public static final String NAME = "aztecabyss_stats";

    /** How many arenas we keep separate best-round records for. */
    public static final int MAP_COUNT = 2;

    public record Entry(String name,
                        int soloBestRound, int soloBestSeconds,
                        int mpBestRound, int mpBestSeconds,
                        int totalKills, int totalRevives, int wins,
                        int[] mapBestRound, int[] mapBestSeconds) {

        static Entry empty(String name) {
            return new Entry(name, 0, 0, 0, 0, 0, 0, 0, new int[MAP_COUNT], new int[MAP_COUNT]);
        }

        int bestRound(boolean mp) {
            return mp ? mpBestRound : soloBestRound;
        }

        int bestSeconds(boolean mp) {
            return mp ? mpBestSeconds : soloBestSeconds;
        }

        /** Best round this player has reached on one specific arena. */
        public int bestRoundOnMap(int mapId) {
            return (mapId >= 0 && mapId < mapBestRound.length) ? mapBestRound[mapId] : 0;
        }

        public int bestSecondsOnMap(int mapId) {
            return (mapId >= 0 && mapId < mapBestSeconds.length) ? mapBestSeconds[mapId] : 0;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static SavedData.Factory<AbyssStats> factory() {
        return new SavedData.Factory<>(AbyssStats::new, AbyssStats::load, null);
    }

    public static AbyssStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public void record(UUID id, String name, int roundReached, int survivalSeconds,
                       int totalKills, int totalRevives, boolean victory, boolean multiplayer, int mapId) {
        Entry p = entries.getOrDefault(id, Entry.empty(name));
        int soloRound = p.soloBestRound();
        int soloSecs = p.soloBestSeconds();
        int mpRound = p.mpBestRound();
        int mpSecs = p.mpBestSeconds();
        if (multiplayer) {
            mpRound = Math.max(mpRound, roundReached);
            mpSecs = Math.max(mpSecs, survivalSeconds);
        } else {
            soloRound = Math.max(soloRound, roundReached);
            soloSecs = Math.max(soloSecs, survivalSeconds);
        }

        // Per-arena personal bests, so each map has its own record to chase.
        int[] mapRounds = p.mapBestRound().clone();
        int[] mapSecs = p.mapBestSeconds().clone();
        if (mapId >= 0 && mapId < MAP_COUNT) {
            mapRounds[mapId] = Math.max(mapRounds[mapId], roundReached);
            mapSecs[mapId] = Math.max(mapSecs[mapId], survivalSeconds);
        }

        entries.put(id, new Entry(name, soloRound, soloSecs, mpRound, mpSecs,
                totalKills, totalRevives, p.wins() + (victory ? 1 : 0), mapRounds, mapSecs));
        setDirty();
    }

    public Entry get(UUID id) {
        return entries.get(id);
    }

    /** A player's best round on one arena (0 if they've never run it). */
    public int bestRoundOnMap(UUID id, int mapId) {
        Entry e = entries.get(id);
        return e == null ? 0 : e.bestRoundOnMap(mapId);
    }

    /** Top players in a category, ranked by longest survival then best round. */
    public List<Map.Entry<UUID, Entry>> top(int n, boolean multiplayer) {
        List<Map.Entry<UUID, Entry>> list = new ArrayList<>(entries.entrySet());
        list.removeIf(e -> e.getValue().bestSeconds(multiplayer) <= 0 && e.getValue().bestRound(multiplayer) <= 0);
        list.sort(Comparator
                .comparingInt((Map.Entry<UUID, Entry> e) -> e.getValue().bestSeconds(multiplayer))
                .thenComparingInt(e -> e.getValue().bestRound(multiplayer))
                .reversed());
        return list.subList(0, Math.min(n, list.size()));
    }

    /** Top players on one specific arena, ranked by survival then round. */
    public List<Map.Entry<UUID, Entry>> topOnMap(int n, int mapId) {
        List<Map.Entry<UUID, Entry>> list = new ArrayList<>(entries.entrySet());
        list.removeIf(e -> e.getValue().bestRoundOnMap(mapId) <= 0);
        list.sort(Comparator
                .comparingInt((Map.Entry<UUID, Entry> e) -> e.getValue().bestSecondsOnMap(mapId))
                .thenComparingInt(e -> e.getValue().bestRoundOnMap(mapId))
                .reversed());
        return list.subList(0, Math.min(n, list.size()));
    }

    // ------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------

    public static AbyssStats load(CompoundTag tag, HolderLookup.Provider provider) {
        AbyssStats stats = new AbyssStats();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            // Older saves predate per-map records; fall back to zeroed arrays.
            int[] mapRounds = padded(e.getIntArray("map_best_round"));
            int[] mapSecs = padded(e.getIntArray("map_best_seconds"));
            stats.entries.put(e.getUUID("id"), new Entry(
                    e.getString("name"),
                    e.getInt("solo_best_round"), e.getInt("solo_best_seconds"),
                    e.getInt("mp_best_round"), e.getInt("mp_best_seconds"),
                    e.getInt("total_kills"), e.getInt("total_revives"), e.getInt("wins"),
                    mapRounds, mapSecs));
        }
        return stats;
    }

    /** Grows (or creates) a stored array to the current map count. */
    private static int[] padded(int[] stored) {
        int[] out = new int[MAP_COUNT];
        if (stored != null) {
            System.arraycopy(stored, 0, out, 0, Math.min(stored.length, MAP_COUNT));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            Entry v = me.getValue();
            CompoundTag e = new CompoundTag();
            e.putUUID("id", me.getKey());
            e.putString("name", v.name());
            e.putInt("solo_best_round", v.soloBestRound());
            e.putInt("solo_best_seconds", v.soloBestSeconds());
            e.putInt("mp_best_round", v.mpBestRound());
            e.putInt("mp_best_seconds", v.mpBestSeconds());
            e.putInt("total_kills", v.totalKills());
            e.putInt("total_revives", v.totalRevives());
            e.putInt("wins", v.wins());
            e.putIntArray("map_best_round", v.mapBestRound());
            e.putIntArray("map_best_seconds", v.mapBestSeconds());
            list.add(e);
        }
        tag.put("entries", list);
        return tag;
    }
}
