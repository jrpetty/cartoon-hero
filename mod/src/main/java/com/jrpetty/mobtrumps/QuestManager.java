package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Three daily quests, the same for everyone (rolled from the day number), paid
 * out in emeralds. Progress is measured against a snapshot of each player's
 * counters taken when their quest day starts, so nothing needs extra hooks.
 */
public final class QuestManager {

    /** id, description, metric key, target, emerald reward. */
    private record Quest(String desc, String metric, int target, int reward) {
    }

    private static final List<Quest> POOL = List.of(
            new Quest("Win 1 PvP duel", "duel_wins", 1, 5),
            new Quest("Win 2 PvP duels", "duel_wins", 2, 10),
            new Quest("Win 1 battle vs the CPU", "battle_wins", 1, 3),
            new Quest("Win 3 battles vs the CPU", "battle_wins", 3, 8),
            new Quest("Hunt 10 mobs", "kills", 10, 4),
            new Quest("Hunt 25 mobs", "kills", 25, 8),
            new Quest("Play 8 stat picks in battles", "picks", 8, 4),
            new Quest("Play 20 stat picks in battles", "picks", 20, 8));

    private QuestManager() {
    }

    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /** The three quest indices everyone shares today. */
    private static int[] todaysQuests() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < POOL.size(); i++) ids.add(i);
        java.util.Collections.shuffle(ids, new Random(today() * 31L + 7L));
        return new int[]{ids.get(0), ids.get(1), ids.get(2)};
    }

    private static int metric(ServerPlayer player, String key) {
        return switch (key) {
            case "duel_wins" -> player.getData(ModAttachments.DUEL_WINS.get());
            case "battle_wins" -> StatsTracker.count(player, "battle_wins");
            case "kills" -> StatsTracker.totalKills(player);
            case "picks" -> {
                int total = 0;
                for (com.jrpetty.mobtrumps.game.Stat s : com.jrpetty.mobtrumps.game.Stat.values()) {
                    total += StatsTracker.count(player, "pick_" + s.key());
                }
                yield total;
            }
            default -> 0;
        };
    }

    /** Roll the player onto today's quests if their stored day is stale. */
    private static Map<String, Integer> freshState(ServerPlayer player) {
        Map<String, Integer> state = new HashMap<>(player.getData(ModAttachments.QUEST_STATE.get()));
        long day = today();
        if (state.getOrDefault("day", -1) != (int) (day % Integer.MAX_VALUE)) {
            state.clear();
            state.put("day", (int) (day % Integer.MAX_VALUE));
            int[] quests = todaysQuests();
            for (int i = 0; i < 3; i++) {
                Quest q = POOL.get(quests[i]);
                state.put("q" + i, quests[i]);
                state.put("b" + i, metric(player, q.metric())); // baseline snapshot
                state.put("c" + i, 0);
            }
            player.setData(ModAttachments.QUEST_STATE.get(), Map.copyOf(state));
        }
        return state;
    }

    public static int show(ServerPlayer player) {
        Map<String, Integer> state = freshState(player);
        player.sendSystemMessage(Component.literal("═══ DAILY QUESTS ═══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 0; i < 3; i++) {
            Quest q = POOL.get(state.get("q" + i));
            int progress = Math.min(q.target(), metric(player, q.metric()) - state.get("b" + i));
            boolean claimed = state.getOrDefault("c" + i, 0) == 1;
            boolean done = progress >= q.target();
            Component line;
            if (claimed) {
                line = Component.literal("  [DONE] " + q.desc()).withStyle(ChatFormatting.DARK_GRAY);
            } else if (done) {
                line = Component.literal("  " + q.desc() + " — complete! ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(BattleCommands.button("[Claim " + q.reward() + " emeralds]",
                                "/mobtrumps quest claim " + i, ChatFormatting.GOLD,
                                "Collect your reward"));
            } else {
                line = Component.literal("  " + q.desc() + "  ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(progress + "/" + q.target())
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("  → " + q.reward() + " emeralds")
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }
        player.sendSystemMessage(Component.literal("Quests reset daily — everyone gets the same three.")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    public static int claim(ServerPlayer player, int slot) {
        if (slot < 0 || slot > 2) return 0;
        Map<String, Integer> state = new HashMap<>(freshState(player));
        Quest q = POOL.get(state.get("q" + slot));
        if (state.getOrDefault("c" + slot, 0) == 1) {
            player.sendSystemMessage(Component.literal("Already claimed.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        int progress = metric(player, q.metric()) - state.get("b" + slot);
        if (progress < q.target()) {
            player.sendSystemMessage(Component.literal("Not finished yet: " + progress + "/" + q.target())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        state.put("c" + slot, 1);
        player.setData(ModAttachments.QUEST_STATE.get(), Map.copyOf(state));
        CardActions.giveEmeralds(player, q.reward());
        player.sendSystemMessage(Component.literal("Quest complete — " + q.reward() + " emeralds!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        return 1;
    }
}
