package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Stat;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The /mobtrumps command: chat-driven Top Trumps battles.
 *
 *   /mobtrumps battle [deck_size]  - start a battle vs the CPU (default 20 cards)
 *   /mobtrumps duel <player>       - challenge another player to a duel
 *   /mobtrumps duel accept|decline - answer a challenge (clickable)
 *   /mobtrumps play <stat>         - pick a stat on your turn (clickable)
 *   /mobtrumps next                - let the CPU take its pick (clickable)
 *   /mobtrumps forfeit             - give up the current battle or duel
 */
public final class BattleCommands {

    private static final int DEFAULT_DECK = 20;
    private static final Map<UUID, Battle> BATTLES = new ConcurrentHashMap<>();

    private BattleCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mobtrumps")
                .then(Commands.literal("battle")
                        .executes(ctx -> startBattle(ctx.getSource(), DEFAULT_DECK))
                        .then(Commands.argument("deck_size", IntegerArgumentType.integer(4, 80))
                                .executes(ctx -> startBattle(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "deck_size")))))
                .then(Commands.literal("play")
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Stat s : Stat.values()) {
                                        builder.suggest(s.key());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> play(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "stat")))))
                .then(Commands.literal("next")
                        .executes(ctx -> cpuTurn(ctx.getSource())))
                .then(Commands.literal("forfeit")
                        .executes(ctx -> forfeit(ctx.getSource())))
                .then(Commands.literal("duel")
                        .then(Commands.literal("accept")
                                .executes(ctx -> DuelManager.accept(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("decline")
                                .executes(ctx -> DuelManager.decline(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> DuelManager.challenge(
                                        ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player"))))));
    }

    // --- command handlers ---

    private static int startBattle(CommandSourceStack source, int deckSize) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Battle battle = new Battle(deckSize, ThreadLocalRandom.current());
        BATTLES.put(player.getUUID(), battle);

        player.sendSystemMessage(Component.literal("=== MOB TRUMPS ===")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(
                        "The deck is dealt: " + battle.playerCardCount() + " cards each. "
                        + "Higher stat wins the round; win every card to win the battle!")
                .withStyle(ChatFormatting.GRAY));
        promptRound(player, battle);
        return 1;
    }

    private static int play(CommandSourceStack source, String statKey) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (DuelManager.isInDuel(player)) {
            return DuelManager.play(player, statKey);
        }
        Battle battle = BATTLES.get(player.getUUID());
        if (battle == null || battle.isFinished()) {
            return noBattle(player);
        }
        if (battle.getTurn() != Battle.Side.PLAYER) {
            player.sendSystemMessage(Component.literal("The CPU holds the pick — click [Continue].")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Stat stat = Stat.byKey(statKey);
        if (stat == null) {
            player.sendSystemMessage(Component.literal(
                            "Unknown stat '" + statKey + "'. Pick one of: health, attack, size, speed, farmable, rarity.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        resolveRound(player, battle, stat);
        return 1;
    }

    private static int cpuTurn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Battle battle = BATTLES.get(player.getUUID());
        if (battle == null || battle.isFinished()) {
            return noBattle(player);
        }
        if (battle.getTurn() != Battle.Side.CPU) {
            player.sendSystemMessage(Component.literal("It's your pick — choose a stat on your card!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        resolveRound(player, battle, battle.cpuChoice());
        return 1;
    }

    private static int forfeit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (DuelManager.isInDuel(player)) {
            return DuelManager.forfeit(player);
        }
        if (BATTLES.remove(player.getUUID()) == null) {
            return noBattle(player);
        }
        player.sendSystemMessage(Component.literal("You flip the table and forfeit the battle.")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int noBattle(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("No battle running. Start one: ")
                .withStyle(ChatFormatting.GRAY)
                .append(button("[Battle!]", "/mobtrumps battle", ChatFormatting.GREEN,
                        "Start a Mob Trumps battle")));
        return 0;
    }

    // --- round flow ---

    private static void resolveRound(ServerPlayer player, Battle battle, Stat stat) {
        Battle.Side chooser = battle.getTurn();
        Battle.RoundResult result = battle.playRound(stat);

        MutableComponent reveal = Component.literal("Round " + result.round() + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(cardName(result.playerCard()))
                .append(statValue(stat, result.playerCard().stat(stat)))
                .append(Component.literal(" vs ").withStyle(ChatFormatting.DARK_GRAY))
                .append(cardName(result.cpuCard()))
                .append(statValue(stat, result.cpuCard().stat(stat)));
        if (chooser == Battle.Side.CPU) {
            reveal.append(Component.literal("  (CPU picked " + stat.label + ")")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        player.sendSystemMessage(reveal);

        switch (result.winner()) {
            case PLAYER -> player.sendSystemMessage(Component.literal("You take the round!")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            case CPU -> player.sendSystemMessage(Component.literal("The CPU takes the round.")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            case NONE -> player.sendSystemMessage(Component.literal("Tie! Both cards go into the pot.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }

        if (battle.isFinished()) {
            endBattle(player, battle);
        } else {
            promptRound(player, battle);
        }
    }

    private static void promptRound(ServerPlayer player, Battle battle) {
        player.sendSystemMessage(Component.literal(
                        "You: " + battle.playerCardCount()
                        + " | CPU: " + battle.cpuCardCount()
                        + (battle.potCount() > 0 ? " | Pot: " + battle.potCount() : ""))
                .withStyle(ChatFormatting.DARK_GRAY));

        if (battle.getTurn() == Battle.Side.PLAYER) {
            MobCard card = battle.playerTopCard();
            player.sendSystemMessage(Component.literal("Your card: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(cardName(card)));
            MutableComponent picks = Component.literal("Pick a stat: ").withStyle(ChatFormatting.GRAY);
            for (Stat stat : Stat.values()) {
                picks.append(button(
                        "[" + stat.shortLabel + " " + card.stat(stat) + "]",
                        "/mobtrumps play " + stat.key(),
                        MobCardItem.statColor(stat),
                        "Play " + stat.label + " (" + card.stat(stat) + ")"));
                picks.append(Component.literal(" "));
            }
            player.sendSystemMessage(picks);
        } else {
            player.sendSystemMessage(Component.literal("The CPU holds the pick... ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(button("[Continue >]", "/mobtrumps next",
                            ChatFormatting.GREEN, "See the CPU's move")));
        }
    }

    private static void endBattle(ServerPlayer player, Battle battle) {
        BATTLES.remove(player.getUUID());
        switch (battle.getWinner()) {
            case PLAYER -> {
                player.sendSystemMessage(Component.literal("VICTORY! You hold every card!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                ItemStack reward = new ItemStack(ModItems.CARD_PACK.get());
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
                player.sendSystemMessage(Component.literal("Reward: 1 Mob Card Pack")
                        .withStyle(ChatFormatting.YELLOW));
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            case CPU -> player.sendSystemMessage(Component.literal("DEFEAT! The CPU took your whole deck...")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            case NONE -> player.sendSystemMessage(Component.literal("A draw — the pot swallowed everything.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }
        player.sendSystemMessage(Component.literal("Play again? ")
                .withStyle(ChatFormatting.GRAY)
                .append(button("[Battle!]", "/mobtrumps battle", ChatFormatting.GREEN,
                        "Start a new Mob Trumps battle")));
    }

    // --- chat component helpers ---

    static Component button(String label, String command, ChatFormatting color, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** Card name coloured by tier; hovering shows the full stat block. */
    static Component cardName(MobCard card) {
        MutableComponent stats = Component.literal(card.displayName())
                .withStyle(MobCardItem.tierColor(card.tier()), ChatFormatting.BOLD)
                .append(Component.literal("\n★ " + card.tier().label() + " ★")
                        .withStyle(MobCardItem.tierColor(card.tier())));
        for (Stat stat : Stat.values()) {
            stats.append(Component.literal("\n" + stat.label + ": ")
                            .withStyle(MobCardItem.statColor(stat)))
                    .append(Component.literal(String.valueOf(card.stat(stat)))
                            .withStyle(ChatFormatting.WHITE));
        }
        return Component.literal(card.displayName())
                .withStyle(style -> style
                        .withColor(MobCardItem.tierColor(card.tier()))
                        .withBold(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, stats)));
    }

    static Component statValue(Stat stat, int value) {
        return Component.literal(" (" + stat.shortLabel + " " + value + ")")
                .withStyle(MobCardItem.statColor(stat));
    }
}
