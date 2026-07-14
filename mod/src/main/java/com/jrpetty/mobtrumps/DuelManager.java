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
import net.minecraft.world.item.Items;

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

    private record Pending(UUID challenger, long expiresAt, ItemStack wager, int bet) {
    }

    private static final class Duel {
        final ServerPlayer challenger; // PLAYER side
        final ServerPlayer target;     // CPU side
        final Battle battle;
        ItemStack challengerWager = ItemStack.EMPTY;
        ItemStack targetWager = ItemStack.EMPTY;
        int challengerBet = 0;
        int targetBet = 0;

        Duel(ServerPlayer challenger, ServerPlayer target, Battle battle) {
            this.challenger = challenger;
            this.target = target;
            this.battle = battle;
        }

        boolean isWager() {
            return !challengerWager.isEmpty() || challengerBet > 0;
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
        return challenge(challenger, target, false);
    }

    public static int challenge(ServerPlayer challenger, ServerPlayer target, boolean wager) {
        if (challenger.getUUID().equals(target.getUUID())) {
            challenger.sendSystemMessage(err("You can't duel yourself."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            challenger.sendSystemMessage(err("Someone is already in a duel."));
            return 0;
        }

        ItemStack stake = ItemStack.EMPTY;
        if (wager) {
            ItemStack held = challenger.getMainHandItem();
            if (MobCardItem.cardOf(held) == null) {
                challenger.sendSystemMessage(err("Hold the mob card you want to wager."));
                return 0;
            }
            stake = held.copyWithCount(1);
            held.shrink(1); // escrow it
        }

        PENDING.put(target.getUUID(),
                new Pending(challenger.getUUID(), System.currentTimeMillis() + CHALLENGE_TTL_MS, stake, 0));

        Component wagerNote = stake.isEmpty() ? Component.empty()
                : Component.literal(" wagering ").withStyle(ChatFormatting.GRAY)
                        .append(stake.getHoverName());
        challenger.sendSystemMessage(Component.literal("Challenge sent to " + name(target) + ".")
                .withStyle(ChatFormatting.GREEN).append(wagerNote));
        target.sendSystemMessage(Component.literal(name(challenger)
                        + (stake.isEmpty() ? " challenges you to a Mob Trumps duel! "
                                           : " challenges you to a WAGER duel! "))
                .withStyle(ChatFormatting.GOLD)
                .append(stake.isEmpty() ? Component.empty()
                        : Component.literal("They stake ").withStyle(ChatFormatting.GRAY)
                                .append(stake.getHoverName())
                                .append(Component.literal(" — hold a card to match. ")
                                        .withStyle(ChatFormatting.GRAY)))
                .append(BattleCommands.button("[Accept]", "/mobtrumps duel accept",
                        ChatFormatting.GREEN, "Accept the duel"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps duel decline",
                        ChatFormatting.RED, "Decline the duel")));
        return 1;
    }

    /** Challenge with an emerald wager: both players stake {@code bet} emeralds; winner takes the pot. */
    public static int challengeBet(ServerPlayer challenger, ServerPlayer target, int bet) {
        if (challenger.getUUID().equals(target.getUUID())) {
            challenger.sendSystemMessage(err("You can't duel yourself."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            challenger.sendSystemMessage(err("Someone is already in a duel."));
            return 0;
        }
        if (bet <= 0) {
            challenger.sendSystemMessage(err("The bet must be at least 1 emerald."));
            return 0;
        }
        if (countEmeralds(challenger) < bet) {
            challenger.sendSystemMessage(err("You need " + bet + " emeralds to stake that bet (you have "
                    + countEmeralds(challenger) + ")."));
            return 0;
        }
        takeEmeralds(challenger, bet); // escrow

        PENDING.put(target.getUUID(),
                new Pending(challenger.getUUID(), System.currentTimeMillis() + CHALLENGE_TTL_MS,
                        ItemStack.EMPTY, bet));

        challenger.sendSystemMessage(Component.literal("Challenge sent to " + name(target) + " — staking ")
                .withStyle(ChatFormatting.GREEN)
                .append(emeralds(bet)));
        target.sendSystemMessage(Component.literal(name(challenger) + " challenges you to a WAGER duel! ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("They bet ").withStyle(ChatFormatting.GRAY))
                .append(emeralds(bet))
                .append(Component.literal(" — match it to accept. ").withStyle(ChatFormatting.GRAY))
                .append(BattleCommands.button("[Accept]", "/mobtrumps duel accept",
                        ChatFormatting.GREEN, "Match the bet and duel"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps duel decline",
                        ChatFormatting.RED, "Decline the duel")));
        return 1;
    }

    public static int accept(ServerPlayer target) {
        Pending pending = PENDING.remove(target.getUUID());
        if (pending == null) {
            target.sendSystemMessage(err("You have no pending duel challenge."));
            return 0;
        }
        ServerPlayer challenger = target.serverLevel().getServer().getPlayerList().getPlayer(pending.challenger());
        if (pending.expiresAt() < System.currentTimeMillis()) {
            target.sendSystemMessage(err("That duel challenge has expired."));
            returnStake(challenger, pending.wager());
            returnBet(challenger, pending.bet());
            return 0;
        }
        if (challenger == null) {
            target.sendSystemMessage(err("The challenger is no longer online."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            target.sendSystemMessage(err("Someone is already in a duel."));
            returnStake(challenger, pending.wager());
            returnBet(challenger, pending.bet());
            return 0;
        }

        ItemStack targetStake = ItemStack.EMPTY;
        if (!pending.wager().isEmpty()) {
            ItemStack held = target.getMainHandItem();
            if (MobCardItem.cardOf(held) == null) {
                target.sendSystemMessage(err("Hold a mob card to match the wager, then accept."));
                // keep the challenge alive so they can grab a card and retry
                PENDING.put(target.getUUID(), pending);
                return 0;
            }
            targetStake = held.copyWithCount(1);
            held.shrink(1);
        }

        int targetBet = 0;
        if (pending.bet() > 0) {
            if (countEmeralds(target) < pending.bet()) {
                target.sendSystemMessage(err("You need " + pending.bet()
                        + " emeralds to match the bet (you have " + countEmeralds(target) + ")."));
                // keep the challenge alive so they can gather emeralds and retry
                PENDING.put(target.getUUID(), pending);
                return 0;
            }
            takeEmeralds(target, pending.bet());
            targetBet = pending.bet();
        }

        Battle battle = new Battle(DECK_SIZE, ThreadLocalRandom.current());
        Duel duel = new Duel(challenger, target, battle);
        duel.challengerWager = pending.wager();
        duel.targetWager = targetStake;
        duel.challengerBet = pending.bet();
        duel.targetBet = targetBet;
        ACTIVE.put(challenger.getUUID(), duel);
        ACTIVE.put(target.getUUID(), duel);

        MutableComponent intro = Component.literal("=== DUEL: " + name(challenger)
                + " vs " + name(target) + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        challenger.sendSystemMessage(intro);
        target.sendSystemMessage(intro);
        BattleCommands.shuffleSound(challenger);
        BattleCommands.shuffleSound(target);
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
        returnStake(challenger, pending.wager());
        returnBet(challenger, pending.bet());
        if (challenger != null) {
            challenger.sendSystemMessage(Component.literal(name(target) + " declined the duel.")
                    .withStyle(ChatFormatting.RED));
        }
        target.sendSystemMessage(Component.literal("Duel declined.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /** Return an escrowed wager card to its owner (drops if inventory is full). */
    private static void returnStake(ServerPlayer owner, ItemStack stake) {
        if (owner != null && stake != null && !stake.isEmpty()) {
            CardActions.give(owner, stake);
        }
    }

    /** Return escrowed emeralds to their owner (drops any that don't fit). */
    private static void returnBet(ServerPlayer owner, int amount) {
        if (owner != null && amount > 0) {
            giveEmeralds(owner, amount);
        }
    }

    private static int countEmeralds(ServerPlayer player) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.EMERALD)) total += stack.getCount();
        }
        return total;
    }

    /** Remove up to {@code amount} emeralds from the player's inventory. */
    private static void takeEmeralds(ServerPlayer player, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    /** Give emeralds to the player, dropping any that don't fit. */
    private static void giveEmeralds(ServerPlayer player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            ItemStack stack = new ItemStack(Items.EMERALD, stackSize);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackSize;
        }
    }

    /** An emerald-count component with the emerald's green colour. */
    private static Component emeralds(int amount) {
        return Component.literal(amount + (amount == 1 ? " emerald" : " emeralds"))
                .withStyle(ChatFormatting.GREEN);
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
        // pending challenge TO this player: return the challenger's stake
        Pending incoming = PENDING.remove(player.getUUID());
        if (incoming != null) {
            ServerPlayer challenger = player.serverLevel().getServer()
                    .getPlayerList().getPlayer(incoming.challenger());
            returnStake(challenger, incoming.wager());
        }
        // pending challenge FROM this player: cancel it, hand back their stake
        PENDING.entrySet().removeIf(e -> {
            if (e.getValue().challenger().equals(player.getUUID())) {
                if (!e.getValue().wager().isEmpty()) player.drop(e.getValue().wager(), false);
                if (e.getValue().bet() > 0) giveEmeralds(player, e.getValue().bet());
                return true;
            }
            return false;
        });

        Duel duel = ACTIVE.get(player.getUUID());
        if (duel != null) {
            ServerPlayer other = duel.other(player);
            clear(duel);
            // leaving counts as a ranked loss for the quitter
            CollectionTracker.addDuelWin(other);
            applyRanked(other, player);
            if (duel.isWager()) {
                returnStake(other, duel.challengerWager);
                returnStake(other, duel.targetWager);
                int pot = duel.challengerBet + duel.targetBet;
                if (pot > 0) giveEmeralds(other, pot);
                other.sendSystemMessage(Component.literal(name(player)
                                + " left — you win the pot!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else {
                other.sendSystemMessage(Component.literal(name(player) + " left — you win by default.")
                        .withStyle(ChatFormatting.YELLOW));
            }
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
        boolean wager = duel.isWager();
        if (winner == null) {
            sendBoth(duel, Component.literal("The duel is a draw — every stake is returned.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            // draws return each stake to its owner
            returnStake(duel.challenger, duel.challengerWager);
            returnStake(duel.target, duel.targetWager);
            returnBet(duel.challenger, duel.challengerBet);
            returnBet(duel.target, duel.targetBet);
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
        applyRanked(winner, loser);

        if (wager) {
            // winner takes both wagered cards
            if (!duel.challengerWager.isEmpty() || !duel.targetWager.isEmpty()) {
                returnStake(winner, duel.challengerWager);
                returnStake(winner, duel.targetWager);
                winner.sendSystemMessage(Component.literal("You win the wagered cards!")
                        .withStyle(ChatFormatting.GOLD));
                for (var stake : new ItemStack[]{duel.challengerWager, duel.targetWager}) {
                    if (MobCardItem.cardOf(stake) != null) {
                        CollectionTracker.record(winner, MobCardItem.cardOf(stake).id(),
                                MobCardItem.isFoilCard(stake));
                    }
                }
            }
            // winner takes the whole emerald pot
            int pot = duel.challengerBet + duel.targetBet;
            if (pot > 0) {
                giveEmeralds(winner, pot);
                winner.sendSystemMessage(Component.literal("You win the pot of ")
                        .withStyle(ChatFormatting.GOLD).append(emeralds(pot)).append(Component.literal("!")
                                .withStyle(ChatFormatting.GOLD)));
            }
        } else {
            giveEmeralds(winner, 3);
            winner.sendSystemMessage(Component.literal("Reward: 3 emeralds")
                    .withStyle(ChatFormatting.YELLOW));
        }
        winner.serverLevel().playSound(null, winner.getX(), winner.getY(), winner.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** Update ranked standings and tell both players their new rating. */
    private static void applyRanked(ServerPlayer winner, ServerPlayer loser) {
        if (winner == null || loser == null) return;
        Leaderboard board = Leaderboard.get(winner.serverLevel().getServer());
        int[] ratings = board.recordDuel(winner, loser);
        winner.sendSystemMessage(Component.literal("Rating: " + ratings[0]
                + " (rank #" + board.rankOf(winner.getUUID()) + ")").withStyle(ChatFormatting.AQUA));
        loser.sendSystemMessage(Component.literal("Rating: " + ratings[1]
                + " (rank #" + board.rankOf(loser.getUUID()) + ")").withStyle(ChatFormatting.GRAY));
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
