package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
 * players still appear. Ratings use a simple Elo update. Play is organised
 * into seasons: when a season ends every ranked player is soft-reset toward
 * the mean, keeps a permanent tier badge, and is paid emeralds for their final
 * tier — rewards for anyone offline are queued and handed out on next login.
 */
public class Leaderboard extends SavedData {

    public static final String NAME = "mobtrumps_leaderboard";
    private static final int K = 32;
    public static final int START = 1000;

    public record Entry(UUID id, String name, int rating, int wins, int losses, int peak) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Integer> pendingRewards = new LinkedHashMap<>();
    private final Map<UUID, String> pendingBadges = new LinkedHashMap<>();
    private int season = 1;
    private long seasonStartMs = 0L;

    public static Leaderboard get(MinecraftServer server) {
        Leaderboard board = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(Leaderboard::new, Leaderboard::load, null), NAME);
        if (board.seasonStartMs == 0L) {
            board.seasonStartMs = System.currentTimeMillis();
            board.setDirty();
        }
        return board;
    }

    public int season() {
        return season;
    }

    public long seasonStartMs() {
        return seasonStartMs;
    }

    private static long seasonLengthMs() {
        try {
            return Config.SEASON_DAYS.get() * 86_400_000L;
        } catch (IllegalStateException notLoaded) {
            return 7L * 86_400_000L;
        }
    }

    public long msLeft() {
        return Math.max(0L, seasonStartMs + seasonLengthMs() - System.currentTimeMillis());
    }

    /** Apply a duel result and return the winner's and loser's new ratings. */
    public int[] recordDuel(ServerPlayer winner, ServerPlayer loser) {
        Entry w = entries.getOrDefault(winner.getUUID(),
                new Entry(winner.getUUID(), name(winner), START, 0, 0, START));
        Entry l = entries.getOrDefault(loser.getUUID(),
                new Entry(loser.getUUID(), name(loser), START, 0, 0, START));

        double expW = 1.0 / (1.0 + Math.pow(10, (l.rating() - w.rating()) / 400.0));
        double expL = 1.0 - expW;
        int newW = (int) Math.round(w.rating() + K * (1 - expW));
        int newL = (int) Math.round(l.rating() + K * (0 - expL));

        entries.put(winner.getUUID(), new Entry(winner.getUUID(), name(winner), newW,
                w.wins() + 1, w.losses(), Math.max(w.peak(), newW)));
        entries.put(loser.getUUID(), new Entry(loser.getUUID(), name(loser), newL,
                l.wins(), l.losses() + 1, l.peak()));
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

    public int rankedCount() {
        return entries.size();
    }

    public Entry entry(UUID id) {
        return entries.get(id);
    }

    // --- seasons ---

    /** Roll to the next season if the timer has elapsed. Call periodically. */
    public void maybeRollover(MinecraftServer server) {
        if (msLeft() <= 0L && !entries.isEmpty()) {
            endSeason(server);
        }
    }

    /** End the current season now: badges, rewards, soft reset. */
    public void endSeason(MinecraftServer server) {
        int ending = season;
        List<Entry> ranked = top(entries.size());
        for (Entry e : ranked) {
            if (e.wins() + e.losses() == 0) continue; // must have played
            String badge = "S" + ending + ":" + RankTier.badgeLabel(e.rating());
            RankTier tier = RankTier.of(e.rating());
            pendingBadges.put(e.id(), badge);
            pendingRewards.merge(e.id(), tier.reward, Integer::sum);
        }
        // soft reset: pull everyone halfway back to the mean, wipe W/L
        Map<UUID, Entry> reset = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            int r = START + (e.rating() - START) / 2;
            reset.put(e.id(), new Entry(e.id(), e.name(), r, 0, 0, r));
        }
        entries.clear();
        entries.putAll(reset);
        season++;
        seasonStartMs = System.currentTimeMillis();
        setDirty();

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "=== RANKED SEASON " + ending + " HAS ENDED — SEASON " + season + " BEGINS! ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "Ratings have been soft-reset. Claim your season rewards — climb again!")
                .withStyle(ChatFormatting.YELLOW), false);
        // hand out to everyone currently online right away
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            claimPending(p);
        }
    }

    /** Grant any queued season rewards/badge to a player (call on login). */
    public void claimPending(ServerPlayer player) {
        UUID id = player.getUUID();
        String badge = pendingBadges.remove(id);
        Integer reward = pendingRewards.remove(id);
        if (badge == null && reward == null) {
            return;
        }
        if (badge != null) {
            var badges = new ArrayList<>(player.getData(ModAttachments.RANKED_BADGES.get()));
            if (!badges.contains(badge)) badges.add(badge);
            player.setData(ModAttachments.RANKED_BADGES.get(), List.copyOf(badges));
            player.sendSystemMessage(Component.literal("Season reward: earned the ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(badge.replace(":", " "))
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                    .append(Component.literal(" badge!").withStyle(ChatFormatting.GOLD)));
        }
        if (reward != null && reward > 0) {
            CardActions.giveEmeralds(player, reward);
            player.sendSystemMessage(Component.literal("Season payout: " + reward + " emeralds.")
                    .withStyle(ChatFormatting.GREEN));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
        setDirty();
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
            t.putInt("peak", e.peak());
            list.add(t);
        }
        tag.put("entries", list);
        tag.putInt("season", season);
        tag.putLong("seasonStart", seasonStartMs);

        ListTag pend = new ListTag();
        for (var e : pendingRewards.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            t.putInt("reward", e.getValue());
            if (pendingBadges.containsKey(e.getKey())) t.putString("badge", pendingBadges.get(e.getKey()));
            pend.add(t);
        }
        // badges without a reward entry (shouldn't happen, but be safe)
        for (var e : pendingBadges.entrySet()) {
            if (pendingRewards.containsKey(e.getKey())) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            t.putString("badge", e.getValue());
            pend.add(t);
        }
        tag.put("pending", pend);
        return tag;
    }

    public static Leaderboard load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        Leaderboard board = new Leaderboard();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("id");
            int rating = t.getInt("rating");
            int peak = t.contains("peak") ? t.getInt("peak") : rating;
            board.entries.put(id, new Entry(id, t.getString("name"),
                    rating, t.getInt("wins"), t.getInt("losses"), peak));
        }
        board.season = tag.contains("season") ? tag.getInt("season") : 1;
        board.seasonStartMs = tag.getLong("seasonStart");
        ListTag pend = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pend.size(); i++) {
            CompoundTag t = pend.getCompound(i);
            UUID id = t.getUUID("id");
            if (t.contains("reward")) board.pendingRewards.put(id, t.getInt("reward"));
            if (t.contains("badge")) board.pendingBadges.put(id, t.getString("badge"));
        }
        return board;
    }
}
