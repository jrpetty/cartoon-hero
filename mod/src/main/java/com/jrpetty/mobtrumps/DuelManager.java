package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Player-vs-player Top Trumps duels, driven through clickable chat.
 *
 *   /mobtrumps duel &lt;player&gt;   - challenge someone
 *   /mobtrumps duel accept|decline
 *   /mobtrumps play &lt;stat&gt;      - pick on your turn (routed here mid-duel)
 *   /mobtrumps forfeit           - concede the duel
 */
public final class DuelManager {

    private static final int DECK_SIZE = 20;
    private static final long CHALLENGE_TTL_MS = 60_000L;

    /** target UUID -> pending challenge from a challenger. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /** player UUID -> the duel they are in (both players map to the same duel). */
    private static final Map<UUID, Duel> ACTIVE = new ConcurrentHashMap<>();

    private DuelManager() {
    }

    private record Pending(UUID challenger, long expiresAt) {
    }

    private static final class Duel {
        final ServerPlayer challenger; // PLAYER side
        final ServerPlayer target;     // CPU side
        final Battle battle;

        Duel(ServerPlayer challenger, ServerPlayer target, Battle battle) {
            this.challenger = challenger;
            this.target = target;
            this.battle = battle;
        }

        ServerPlayer forSide(Battle.Side side) {
            return side == Battle.Side.PLAYER ? challenger : target;
        }

        Battle.Side sideOf(ServerPlayer player) {
            return player.getUUID().equals(challenger.getUUID()) ? Battle.Side.PLAYER : Battle.Side.CPU;
        }

        ServerPlayer other(ServerPlayer player) {
            return player.getUUID().equals(challenger.getUUID()) ? target : challenger;
        }
    }

    // --- challenge lifecycle ---

    public static int challenge(ServerPlayer challenger, ServerPlayer target) {
        if (challenger.getUUID().equals(target.getUUID())) {
            challenger.sendSystemMessage(err("You can't duel yourself."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            challenger.sendSystemMessage(err("Someone is already in a duel."));
            return 0;
        }
        PENDING.put(target.getUUID(),
                new Pending(challenger.getUUID(), System.currentTimeMillis() + CHALLENGE_TTL_MS));

        challenger.sendSystemMessage(Component.literal("Challenge sent to " + name(target) + ".")
                .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal(name(challenger) + " challenges you to a Mob Trumps duel! ")
                .withStyle(ChatFormatting.GOLD)
                .append(BattleCommands.button("[Accept]", "/mobtrumps duel accept",
                        ChatFormatting.GREEN, "Accept the duel"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps duel decline",
                        ChatFormatting.RED, "Decline the duel")));
        return 1;
    }

    public static int accept(ServerPlayer target) {
        Pending pending = PENDING.remove(target.getUUID());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            target.sendSystemMessage(err("You have no pending duel challenge."));
            return 0;
        }
        ServerPlayer challenger = target.serverLevel().getServer().getPlayerList().getPlayer(pending.challenger());
        if (challenger == null) {
            target.sendSystemMessage(err("The challenger is no longer online."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            target.sendSystemMessage(err("Someone is already in a duel."));
            return 0;
        }

        Battle battle = new Battle(DECK_SIZE, ThreadLocalRandom.current());
        Duel duel = new Duel(challenger, target, battle);
        ACTIVE.put(challenger.getUUID(), duel);
        ACTIVE.put(target.getUUID(), duel);

        MutableComponent intro = Component.literal("=== DUEL: " + name(challenger)
                + " vs " + name(target) + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        challenger.sendSystemMessage(intro);
        target.sendSystemMessage(intro);
        promptTurn(duel);
        return 1;
    }

    public static int decline(ServerPlayer target) {
        Pending pending = PENDING.remove(target.getUUID());
        if (pending == null) {
            target.sendSystemMessage(err("You have no pending duel challenge."));
            return 0;
        }
        ServerPlayer challenger = target.serverLevel().getServer().getPlayerList().getPlayer(pending.challenger());
        if (challenger != null) {
            challenger.sendSystemMessage(Component.literal(name(target) + " declined the duel.")
                    .withStyle(ChatFormatting.RED));
        }
        target.sendSystemMessage(Component.literal("Duel declined.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    // --- in-duel play ---

    public static boolean isInDuel(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static int play(ServerPlayer player, String statKey) {
        Duel duel = ACTIVE.get(player.getUUID());
        if (duel == null) {
            return 0;
        }
        if (duel.sideOf(player) != duel.battle.getTurn()) {
            player.sendSystemMessage(err("It's not your turn — waiting on " + name(duel.other(player)) + "."));
            return 0;
        }
        Stat stat = Stat.byKey(statKey);
        if (stat == null) {
            player.sendSystemMessage(err("Unknown stat '" + statKey + "'."));
            return 0;
        }
        resolveRound(duel, stat);
        return 1;
    }

    public static int forfeit(ServerPlayer player) {
        Duel duel = ACTIVE.get(player.getUUID());
        if (duel == null) {
            return 0;
        }
        ServerPlayer winner = duel.other(player);
        endDuel(duel, winner, player, true);
        return 1;
    }

    public static void handleLogout(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        Duel duel = ACTIVE.get(player.getUUID());
        if (duel != null) {
            ServerPlayer other = duel.other(player);
            clear(duel);
            other.sendSystemMessage(Component.literal(name(player) + " left — the duel is over.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    // --- round flow ---

    private static void resolveRound(Duel duel, Stat stat) {
        Battle.Side chooser = duel.battle.getTurn();
        ServerPlayer picker = duel.forSide(chooser);
        Battle.RoundResult result = duel.battle.playRound(stat);

        MutableComponent reveal = Component.literal("Round " + result.round() + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(name(duel.challenger) + " ").withStyle(ChatFormatting.WHITE))
                .append(BattleCommands.cardName(result.playerCard()))
                .append(BattleCommands.statValue(stat, result.playerCard().stat(stat)))
                .append(Component.literal(" vs ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(name(duel.target) + " ").withStyle(ChatFormatting.WHITE))
                .append(BattleCommands.cardName(result.cpuCard()))
                .append(BattleCommands.statValue(stat, result.cpuCard().stat(stat)))
                .append(Component.literal("  (" + name(picker) + " picked " + stat.label + ")")
                        .withStyle(ChatFormatting.DARK_GRAY));

        MutableComponent outcome = switch (result.winner()) {
            case PLAYER -> Component.literal(name(duel.challenger) + " takes the round!")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case CPU -> Component.literal(name(duel.target) + " takes the round!")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case NONE -> Component.literal("Tie! Both cards go into the pot.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        };

        sendBoth(duel, reveal);
        sendBoth(duel, outcome);

        if (result.winner() == Battle.Side.PLAYER) {
            roundSound(duel.challenger, 1.3F);
            roundSound(duel.target, 0.7F);
        } else if (result.winner() == Battle.Side.CPU) {
            roundSound(duel.challenger, 0.7F);
            roundSound(duel.target, 1.3F);
        } else {
            roundSound(duel.challenger, 1.0F);
            roundSound(duel.target, 1.0F);
        }

        if (duel.battle.isFinished()) {
            Battle.Side winSide = duel.battle.getWinner();
            if (winSide == Battle.Side.NONE) {
                endDuel(duel, null, null, false);
            } else {
                endDuel(duel, duel.forSide(winSide), duel.forSide(winSide == Battle.Side.PLAYER
                        ? Battle.Side.CPU : Battle.Side.PLAYER), false);
            }
        } else {
            promptTurn(duel);
        }
    }

    private static void promptTurn(Duel duel) {
        ServerPlayer chooser = duel.forSide(duel.battle.getTurn());
        ServerPlayer waiter = duel.other(chooser);

        int challengerCards = duel.battle.playerCardCount();
        int targetCards = duel.battle.cpuCardCount();
        int pot = duel.battle.potCount();
        MutableComponent score = Component.literal(name(duel.challenger) + ": " + challengerCards
                + " | " + name(duel.target) + ": " + targetCards
                + (pot > 0 ? " | Pot: " + pot : "")).withStyle(ChatFormatting.DARK_GRAY);
        sendBoth(duel, score);

        MobCard card = duel.battle.getTurn() == Battle.Side.PLAYER
                ? duel.battle.playerTopCard() : duel.battle.cpuTopCard();
        MutableComponent picks = Component.literal("Your card: ").withStyle(ChatFormatting.GRAY)
                .append(BattleCommands.cardName(card))
                .append(Component.literal("\nPick a stat: ").withStyle(ChatFormatting.GRAY));
        for (Stat stat : Stat.values()) {
            picks.append(BattleCommands.button(
                    "[" + stat.shortLabel + " " + card.stat(stat) + "]",
                    "/mobtrumps play " + stat.key(),
                    MobCardItem.statColor(stat),
                    "Play " + stat.label + " (" + card.stat(stat) + ")"));
            picks.append(Component.literal(" "));
        }
        chooser.sendSystemMessage(picks);
        waiter.sendSystemMessage(Component.literal("Waiting for " + name(chooser) + " to pick...")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void endDuel(Duel duel, ServerPlayer winner, ServerPlayer loser, boolean forfeit) {
        clear(duel);
        if (winner == null) {
            sendBoth(duel, Component.literal("The duel is a draw — the pot swallowed everything.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            return;
        }
        if (forfeit) {
            sendBoth(duel, Component.literal(name(loser) + " forfeits — " + name(winner) + " wins!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            sendBoth(duel, Component.literal(name(winner) + " wins the duel!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        CollectionTracker.addDuelWin(winner);
        ItemStack reward = new ItemStack(ModItems.CARD_PACK.get());
        if (!winner.getInventory().add(reward)) {
            winner.drop(reward, false);
        }
        winner.sendSystemMessage(Component.literal("Reward: 1 Mob Card Pack")
                .withStyle(ChatFormatting.YELLOW));
        winner.serverLevel().playSound(null, winner.getX(), winner.getY(), winner.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void clear(Duel duel) {
        ACTIVE.remove(duel.challenger.getUUID());
        ACTIVE.remove(duel.target.getUUID());
    }

    private static void sendBoth(Duel duel, Component message) {
        duel.challenger.sendSystemMessage(message);
        duel.target.sendSystemMessage(message);
    }

    private static void roundSound(ServerPlayer player, float pitch) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, pitch);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
