package com.jrpetty.aztecabyss.event;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.data.AbyssStats;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import com.jrpetty.aztecabyss.round.RunState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /abyss stats}, {@code /abyss leaderboard solo}, and
 * {@code /abyss leaderboard multiplayer} - surfaces the persistent
 * {@link AbyssStats} leaderboards (ranked by longest survival) and the caller's
 * own record and cooldown.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class AbyssCommands {

    private AbyssCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("abyss")
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource())))
                .then(Commands.literal("leaderboard")
                        .executes(ctx -> leaderboard(ctx.getSource(), false))
                        .then(Commands.literal("solo").executes(ctx -> leaderboard(ctx.getSource(), false)))
                        .then(Commands.literal("multiplayer").executes(ctx -> leaderboard(ctx.getSource(), true)))
                        .then(Commands.literal("mp").executes(ctx -> leaderboard(ctx.getSource(), true))));
        event.getDispatcher().register(root);
    }

    private static String fmtTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return m + "m " + s + "s";
    }

    private static int stats(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        AbyssStats.Entry e = AbyssStats.get(source.getServer()).get(player.getUUID());

        source.sendSuccess(() -> Component.literal("§6— Your Abyss record —"), false);
        source.sendSuccess(() -> Component.literal("§7Best round: §e" + rs.getBestRound()
                + " §8| §7Lifetime kills: §e" + rs.getTotalKills()
                + " §8| §7Revives: §e" + rs.getTotalRevives()), false);
        if (e != null) {
            source.sendSuccess(() -> Component.literal("§7Solo best: §fround §e" + e.soloBestRound()
                    + " §7— survived §e" + fmtTime(e.soloBestSeconds())), false);
            source.sendSuccess(() -> Component.literal("§7Co-op best: §fround §e" + e.mpBestRound()
                    + " §7— survived §e" + fmtTime(e.mpBestSeconds())), false);
            if (e.wins() > 0) {
                source.sendSuccess(() -> Component.literal("§6Round-20 clears: §e" + e.wins() + " ★"), false);
            }
        }
        long remaining = rs.getCooldownUntil() - System.currentTimeMillis();
        if (remaining > 0) {
            long h = remaining / 3_600_000L;
            long m = (remaining / 60_000L) % 60;
            source.sendSuccess(() -> Component.literal("§7Sealed for: §c" + h + "h " + m + "m"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Status: §aready to enter"), false);
        }
        return 1;
    }

    private static int leaderboard(CommandSourceStack source, boolean multiplayer) {
        AbyssStats stats = AbyssStats.get(source.getServer());
        List<Map.Entry<UUID, AbyssStats.Entry>> top = stats.top(10, multiplayer);
        String label = multiplayer ? "Co-op" : "Solo";
        source.sendSuccess(() -> Component.literal("§6— Aztec Abyss " + label + " Leaderboard §7(by survival)§6 —"), false);
        if (top.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No " + label.toLowerCase() + " runs recorded yet. Be the first."), false);
            return 1;
        }
        int[] rank = {1};
        for (Map.Entry<UUID, AbyssStats.Entry> me : top) {
            AbyssStats.Entry v = me.getValue();
            int r = rank[0]++;
            int secs = multiplayer ? v.mpBestSeconds() : v.soloBestSeconds();
            int rnd = multiplayer ? v.mpBestRound() : v.soloBestRound();
            source.sendSuccess(() -> Component.literal("§e#" + r + " §f" + v.name()
                    + " §7— survived §a" + fmtTime(secs) + " §7(round §c" + rnd + "§7)"
                    + (v.wins() > 0 ? " §6★" + v.wins() : "")), false);
        }
        return 1;
    }
}
