package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who you have beaten, who has beaten you, and how the last few went.
 *
 * <p>A duelling game without a record against the person you keep duelling is
 * missing its most social number. The counters were half here already —
 * something has been quietly writing {@code loss_<name>} into the play stats
 * for a while — but nothing recorded the wins and nothing ever read either
 * back. So "you are 7-2 against Steve" was one table away and never arrived.
 *
 * <p>Head-to-head lives in the existing counter map, keyed by opponent name.
 * The ordered log needs its own list, because order is precisely what a map of
 * counters throws away.
 */
public final class MatchHistory {

    private static final String WIN = "win_";
    private static final String LOSS = "loss_";

    /**
     * How many duels are kept.
     *
     * <p>It rides on the player's save, so this is a real cost, and nobody
     * scrolls further back than this to settle an argument about last night.
     */
    public static final int LOG_SIZE = 20;

    private MatchHistory() {
    }

    /** A record against one opponent. */
    public record Record(String name, int wins, int losses) {

        public int played() {
            return wins + losses;
        }

        /** "7-2", the way anybody would say it out loud. */
        public String line() {
            return wins + "-" + losses;
        }
    }

    /** One finished duel, as stored. */
    public record Bout(String opponent, boolean won, int ratingDelta, long when) {
    }

    // --- writing ---

    /** Record a finished duel from both sides. Call once, with the winner first. */
    public static void record(ServerPlayer winner, ServerPlayer loser,
                              int winnerDelta, int loserDelta) {
        String w = winner.getGameProfile().getName();
        String l = loser.getGameProfile().getName();
        long now = System.currentTimeMillis();
        StatsTracker.bump(winner, WIN + l);
        StatsTracker.bump(loser, LOSS + w);
        push(winner, new Bout(l, true, winnerDelta, now));
        push(loser, new Bout(w, false, loserDelta, now));
    }

    private static void push(ServerPlayer player, Bout bout) {
        List<String> log = new ArrayList<>(player.getData(ModAttachments.MATCH_LOG.get()));
        log.add(0, encode(bout));
        while (log.size() > LOG_SIZE) {
            log.remove(log.size() - 1);
        }
        player.setData(ModAttachments.MATCH_LOG.get(), List.copyOf(log));
    }

    // --- reading ---

    /** The record against one named opponent; zeroes if you have never met. */
    public static Record against(ServerPlayer player, String opponent) {
        var stats = player.getData(ModAttachments.PLAY_STATS.get());
        return new Record(opponent,
                stats.getOrDefault(WIN + opponent, 0),
                stats.getOrDefault(LOSS + opponent, 0));
    }

    /**
     * Everyone this player has a record against, most-played first.
     *
     * <p>Both halves come out of one pass over the counters, so an opponent who
     * has only ever beaten you still appears — which is rather the point.
     */
    public static List<Record> rivals(ServerPlayer player) {
        Map<String, int[]> tally = new HashMap<>();
        for (var e : player.getData(ModAttachments.PLAY_STATS.get()).entrySet()) {
            String key = e.getKey();
            if (key.startsWith(WIN)) {
                tally.computeIfAbsent(key.substring(WIN.length()), k -> new int[2])[0] += e.getValue();
            } else if (key.startsWith(LOSS)) {
                tally.computeIfAbsent(key.substring(LOSS.length()), k -> new int[2])[1] += e.getValue();
            }
        }
        List<Record> out = new ArrayList<>(tally.size());
        for (var e : tally.entrySet()) {
            out.add(new Record(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        out.sort((a, b) -> b.played() - a.played());
        return out;
    }

    /** The recent duels, newest first. */
    public static List<Bout> log(ServerPlayer player) {
        List<String> raw = player.getData(ModAttachments.MATCH_LOG.get());
        List<Bout> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            Bout bout = decode(line);
            if (bout != null) {
                out.add(bout);
            }
        }
        return out;
    }

    // --- the line shown after a duel ---

    /**
     * "You lead Steve 7-2" — the sentence that makes a rivalry a rivalry.
     * Returns null on the very first meeting, when there is no record yet to
     * report and saying "1-0" would be noise.
     */
    public static Component summary(ServerPlayer player, String opponent) {
        Record r = against(player, opponent);
        if (r.played() < 2) {
            return null;
        }
        ChatFormatting colour = r.wins() > r.losses() ? ChatFormatting.GREEN
                : r.wins() < r.losses() ? ChatFormatting.RED : ChatFormatting.YELLOW;
        String verb = r.wins() > r.losses() ? "You lead "
                : r.wins() < r.losses() ? "You trail " : "You are level with ";
        return Component.literal(verb + opponent + " " + r.line())
                .withStyle(colour);
    }

    /** Print the full record and the recent duels. Backs {@code /mobtrumps record}. */
    public static int print(ServerPlayer player) {
        List<Record> rivals = rivals(player);
        List<Bout> recent = log(player);
        player.sendSystemMessage(Component.literal("═══ YOUR RECORD ═══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (rivals.isEmpty()) {
            player.sendSystemMessage(Component.literal("You haven't duelled anyone yet.")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }
        int wins = 0;
        int losses = 0;
        for (Record r : rivals) {
            wins += r.wins();
            losses += r.losses();
        }
        player.sendSystemMessage(Component.literal("Overall " + wins + "-" + losses
                        + " across " + rivals.size() + " opponent" + (rivals.size() == 1 ? "" : "s"))
                .withStyle(ChatFormatting.YELLOW));
        for (Record r : rivals) {
            ChatFormatting colour = r.wins() > r.losses() ? ChatFormatting.GREEN
                    : r.wins() < r.losses() ? ChatFormatting.RED : ChatFormatting.YELLOW;
            player.sendSystemMessage(Component.literal("  " + r.name() + "  ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(r.line()).withStyle(colour))
                    .append(Component.literal("   (" + r.played() + " played)")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (!recent.isEmpty()) {
            player.sendSystemMessage(Component.literal("─── last " + recent.size() + " ───")
                    .withStyle(ChatFormatting.DARK_GRAY));
            for (Bout b : recent) {
                String sign = b.ratingDelta() >= 0 ? "+" : "";
                player.sendSystemMessage(Component.literal(b.won() ? "  WON  " : "  LOST ")
                        .withStyle(b.won() ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .append(Component.literal("vs " + b.opponent()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  " + sign + b.ratingDelta())
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  " + StatsTracker.humanDuration(
                                        System.currentTimeMillis() - b.when()) + " ago")
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        return 1;
    }

    // --- storage format ---

    /** {@code opponent|w/l|delta|epochMillis} — pipe-delimited, like the Guess Who log. */
    private static String encode(Bout b) {
        return b.opponent().replace('|', ' ') + "|" + (b.won() ? "1" : "0")
                + "|" + b.ratingDelta() + "|" + b.when();
    }

    private static Bout decode(String line) {
        String[] bits = line.split("\\|", 4);
        if (bits.length != 4) {
            return null;
        }
        try {
            return new Bout(bits[0], "1".equals(bits[1]),
                    Integer.parseInt(bits[2]), Long.parseLong(bits[3]));
        } catch (NumberFormatException malformed) {
            // a bad line is dropped rather than allowed to throw mid-render
            return null;
        }
    }
}
