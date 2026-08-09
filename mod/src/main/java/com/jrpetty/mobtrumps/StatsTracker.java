package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        player.setData(ModAttachments.PLAY_STATS.get(), stats);
    }

    /** Store {@code value} in {@code key} only if it beats the stored value. */
    public static void recordMax(ServerPlayer player, String key, int value) {
        Map<String, Integer> stats = new HashMap<>(player.getData(ModAttachments.PLAY_STATS.get()));
        if (value > stats.getOrDefault(key, 0)) {
            stats.put(key, value);
            player.setData(ModAttachments.PLAY_STATS.get(), stats);
        }
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
        rankedSection(player, board);
        line(player, "Duels", wins + "W " + losses + "L  (" + winRate + " win rate)");
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

        var badges = player.getData(ModAttachments.RANKED_BADGES.get());
        String name = player.getGameProfile().getName();
        player.sendSystemMessage(Component.literal("╔══════ MOB TRUMPS CARD ══════╗")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  " + name.toUpperCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  ★ " + title(rating, collected) + " ★")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("  Rank: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(RankTier.label(rating))
                        .withStyle(RankTier.of(rating).color, ChatFormatting.BOLD))
                .append(badges.isEmpty() ? Component.empty()
                        : Component.literal("  · " + badges.size() + " season badge"
                                + (badges.size() == 1 ? "" : "s")).withStyle(ChatFormatting.DARK_GRAY)));
        cardStat(player, "Attack (duel wins)", atk, ChatFormatting.RED);
        cardStat(player, "Health (resilience)", hp, ChatFormatting.GREEN);
        cardStat(player, "Collection", col, ChatFormatting.AQUA);
        player.sendSystemMessage(Component.literal("  Rating " + rating + " · " + wins + "W " + losses
                        + "L · " + collected + "/81 cards · " + foils + " holo"
                        + (fav != null ? " · loves " + fav.label : ""))
                .withStyle(ChatFormatting.GRAY));
        rivalsLine(player);
        player.sendSystemMessage(Component.literal("╚═════════════════════════════╝")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    /**
     * The three people you duel most, and how those go.
     *
     * <p>The nemesis line only ever named whoever had beaten you most, which
     * tells you nothing about the rivalries you are winning. A record reads
     * both ways.
     */
    private static void rivalsLine(ServerPlayer player) {
        var rivals = MatchHistory.rivals(player);
        if (rivals.isEmpty()) {
            return;
        }
        MutableComponent line = Component.literal("  Rivals: ").withStyle(ChatFormatting.GRAY);
        int shown = Math.min(3, rivals.size());
        for (int i = 0; i < shown; i++) {
            MatchHistory.Record r = rivals.get(i);
            ChatFormatting colour = r.wins() > r.losses() ? ChatFormatting.GREEN
                    : r.wins() < r.losses() ? ChatFormatting.RED : ChatFormatting.YELLOW;
            if (i > 0) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            line.append(Component.literal(r.name() + " " + r.line()).withStyle(colour));
        }
        player.sendSystemMessage(line);
    }

    /** The dedicated ranked block: tier, rating, peak, season W/L, badges. */
    public static void rankedSection(ServerPlayer player, Leaderboard board) {
        Leaderboard.Entry me = board.entry(player.getUUID());
        int rating = me == null ? Leaderboard.START : me.rating();
        player.sendSystemMessage(Component.literal("  RANKED · Season " + board.season())
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("   Tier: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(RankTier.label(rating))
                        .withStyle(RankTier.of(rating).color, ChatFormatting.BOLD))
                .append(me == null ? Component.literal("  (play a ranked duel to place!)")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal("  rank #" + board.rankOf(player.getUUID())
                                + " / " + board.rankedCount()).withStyle(ChatFormatting.DARK_GRAY)));
        if (me != null) {
            line(player, "  Rating", me.rating() + "  (season peak " + me.peak()
                    + ", best ever " + Math.max(count(player, "ranked_peak"), me.rating()) + ")");
            line(player, "  This season", me.wins() + "W " + me.losses() + "L");
        }
        line(player, "  Lifetime ranked", count(player, "ranked_wins") + "W "
                + count(player, "ranked_losses") + "L");
        var badges = player.getData(ModAttachments.RANKED_BADGES.get());
        line(player, "  Season badges", badges.isEmpty() ? "none yet"
                : badges.size() + " (" + badges.get(badges.size() - 1).replace(":", " ") + " latest)");
        line(player, "  Season ends in", humanDuration(board.msLeft()));
    }

    public static String humanDuration(long ms) {
        long days = ms / 86_400_000L;
        long hours = (ms % 86_400_000L) / 3_600_000L;
        long mins = (ms % 3_600_000L) / 60_000L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
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
