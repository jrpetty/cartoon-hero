package com.jrpetty.mobtrumps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide ranked duel standings, persisted with the world so offline
 * players still appear. Ratings use a simple Elo update on each duel result.
 */
public class Leaderboard extends SavedData {

    public static final String NAME = "mobtrumps_leaderboard";
    private static final int K = 32;
    private static final int START = 1000;

    public record Entry(UUID id, String name, int rating, int wins, int losses) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static Leaderboard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(Leaderboard::new, Leaderboard::load, null), NAME);
    }

    /** Apply a duel result and return the winner's and loser's new ratings. */
    public int[] recordDuel(ServerPlayer winner, ServerPlayer loser) {
        Entry w = entries.getOrDefault(winner.getUUID(),
                new Entry(winner.getUUID(), name(winner), START, 0, 0));
        Entry l = entries.getOrDefault(loser.getUUID(),
                new Entry(loser.getUUID(), name(loser), START, 0, 0));

        double expW = 1.0 / (1.0 + Math.pow(10, (l.rating() - w.rating()) / 400.0));
        double expL = 1.0 - expW;
        int newW = (int) Math.round(w.rating() + K * (1 - expW));
        int newL = (int) Math.round(l.rating() + K * (0 - expL));

        entries.put(winner.getUUID(), new Entry(winner.getUUID(), name(winner), newW, w.wins() + 1, w.losses()));
        entries.put(loser.getUUID(), new Entry(loser.getUUID(), name(loser), newL, l.wins(), l.losses() + 1));
        setDirty();
        return new int[]{newW, newL};
    }

    public List<Entry> top(int n) {
        List<Entry> all = new ArrayList<>(entries.values());
        all.sort(Comparator.comparingInt(Entry::rating).reversed());
        return all.subList(0, Math.min(n, all.size()));
    }

    /** 1-based rank of a player, or -1 if unranked. */
    public int rankOf(UUID id) {
        List<Entry> all = new ArrayList<>(entries.values());
        all.sort(Comparator.comparingInt(Entry::rating).reversed());
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) return i + 1;
        }
        return -1;
    }

    public Entry entry(UUID id) {
        return entries.get(id);
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    // --- persistence ---

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.id());
            t.putString("name", e.name());
            t.putInt("rating", e.rating());
            t.putInt("wins", e.wins());
            t.putInt("losses", e.losses());
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    public static Leaderboard load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        Leaderboard board = new Leaderboard();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("id");
            board.entries.put(id, new Entry(id, t.getString("name"),
                    t.getInt("rating"), t.getInt("wins"), t.getInt("losses")));
        }
        return board;
    }
}
