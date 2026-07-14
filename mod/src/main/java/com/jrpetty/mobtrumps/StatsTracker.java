package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight play counters behind the personal stats dashboard and profile
 * card: favourite stat, nemesis, battles/duels played, and mob kill totals.
 */
public final class StatsTracker {

    private StatsTracker() {
    }

    public static void bump(ServerPlayer player, String key) {
        Map<String, Integer> stats = new HashMap<>(player.getData(ModAttachments.PLAY_STATS.get()));
        stats.merge(key, 1, Integer::sum);
        player.setData(ModAttachments.PLAY_STATS.get(), Map.copyOf(stats));
    }

    public static void recordPick(ServerPlayer player, Stat stat) {
        bump(player, "pick_" + stat.key());
    }

    public static void recordLossTo(ServerPlayer player, String winnerName) {
        bump(player, "loss_" + winnerName);
    }

    /** The stat this player picks most, or null before their first pick. */
    public static Stat favoriteStat(ServerPlayer player) {
        var stats = player.getData(ModAttachments.PLAY_STATS.get());
        Stat best = null;
        int max = 0;
        for (Stat s : Stat.values()) {
            int n = stats.getOrDefault("pick_" + s.key(), 0);
            if (n > max) {
                max = n;
                best = s;
            }
        }
        return best;
    }

    /** The player they've lost to most, or null if they've never lost a duel. */
    public static String nemesis(ServerPlayer player) {
        var stats = player.getData(ModAttachments.PLAY_STATS.get());
        String worst = null;
        int max = 0;
        for (var e : stats.entrySet()) {
            if (e.getKey().startsWith("loss_") && e.getValue() > max) {
                max = e.getValue();
                worst = e.getKey().substring("loss_".length());
            }
        }
        return worst;
    }

    public static int totalKills(ServerPlayer player) {
        int total = 0;
        for (int n : player.getData(ModAttachments.KILLS.get()).values()) total += n;
        return total;
    }

    public static int count(ServerPlayer player, String key) {
        return player.getData(ModAttachments.PLAY_STATS.get()).getOrDefault(key, 0);
    }

    // --- the dashboard and profile card ---

    public static int dashboard(ServerPlayer player) {
        Leaderboard board = Leaderboard.get(player.serverLevel().getServer());
        Leaderboard.Entry me = board.entry(player.getUUID());
        int wins = me == null ? 0 : me.wins();
        int losses = me == null ? 0 : me.losses();
        int games = wins + losses;
        String winRate = games == 0 ? "-" : Math.round(wins * 100f / games) + "%";
        Stat fav = favoriteStat(player);
        String nemesis = nemesis(player);

        player.sendSystemMessage(Component.literal("═══ YOUR MOB TRUMPS STATS ═══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        line(player, "Duels", wins + "W " + losses + "L  (" + winRate + " win rate)");
        if (me != null) {
            line(player, "Rating", me.rating() + "  (rank #" + board.rankOf(player.getUUID()) + ")");
        }
        line(player, "CPU battles won", String.valueOf(count(player, "battle_wins")));
        line(player, "Favourite stat", fav == null ? "none yet" : fav.label
                + " (" + count(player, "pick_" + fav.key()) + " picks)");
        line(player, "Nemesis", nemesis == null ? "no one — undefeated?" : nemesis);
        line(player, "Cards collected", player.getData(ModAttachments.COLLECTED.get()).size() + " / 81");
        line(player, "Holographics", player.getData(ModAttachments.COLLECTED_FOIL.get()).size() + " / 81");
        line(player, "Mobs hunted", String.valueOf(totalKills(player)));
        return 1;
    }

    /** A shareable "profile card" — the player as a Top Trumps card, in chat. */
    public static int profile(ServerPlayer player) {
        Leaderboard board = Leaderboard.get(player.serverLevel().getServer());
        Leaderboard.Entry me = board.entry(player.getUUID());
        int wins = me == null ? 0 : me.wins();
        int losses = me == null ? 0 : me.losses();
        int collected = player.getData(ModAttachments.COLLECTED.get()).size();
        int foils = player.getData(ModAttachments.COLLECTED_FOIL.get()).size();
        int rating = me == null ? 1000 : me.rating();
        Stat fav = favoriteStat(player);

        // scale a few numbers onto the 0-10 card scale for flavour
        int atk = Math.min(10, wins);
        int hp = Math.min(10, 10 - Math.min(9, losses));
        int col = Math.round(collected * 10f / 81f);

        String name = player.getGameProfile().getName();
        player.sendSystemMessage(Component.literal("╔══════ MOB TRUMPS CARD ══════╗")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  " + name.toUpperCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  ★ " + title(rating, collected) + " ★")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        cardStat(player, "Attack (duel wins)", atk, ChatFormatting.RED);
        cardStat(player, "Health (resilience)", hp, ChatFormatting.GREEN);
        cardStat(player, "Collection", col, ChatFormatting.AQUA);
        player.sendSystemMessage(Component.literal("  Rating " + rating + " · " + wins + "W " + losses
                        + "L · " + collected + "/81 cards · " + foils + " holo"
                        + (fav != null ? " · loves " + fav.label : ""))
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("╚═════════════════════════════╝")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static String title(int rating, int collected) {
        if (collected >= 81) return "Master Collector";
        if (rating >= 1200) return "Duel Champion";
        if (rating >= 1100) return "Card Shark";
        if (collected >= 40) return "Seasoned Collector";
        return "Rookie Duelist";
    }

    private static void cardStat(ServerPlayer player, String label, int value, ChatFormatting color) {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) bar.append(i < value ? '#' : '-');
        player.sendSystemMessage(Component.literal("  " + label + ": ").withStyle(color)
                .append(Component.literal("[" + bar + "] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE)));
    }

    private static void line(ServerPlayer player, String label, String value) {
        player.sendSystemMessage(Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
    }
}
