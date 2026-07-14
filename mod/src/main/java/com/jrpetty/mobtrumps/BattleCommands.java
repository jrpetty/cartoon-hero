package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.Difficulty;
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
                .executes(ctx -> menu(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("battle")
                        .executes(ctx -> startBattle(ctx.getSource(), DEFAULT_DECK, Difficulty.NORMAL))
                        .then(Commands.literal("deck")
                                .executes(ctx -> startDeckBattle(ctx.getSource(), Difficulty.NORMAL))
                                .then(Commands.literal("easy")
                                        .executes(ctx -> startDeckBattle(ctx.getSource(), Difficulty.EASY)))
                                .then(Commands.literal("normal")
                                        .executes(ctx -> startDeckBattle(ctx.getSource(), Difficulty.NORMAL)))
                                .then(Commands.literal("hard")
                                        .executes(ctx -> startDeckBattle(ctx.getSource(), Difficulty.HARD))))
                        .then(Commands.literal("easy")
                                .executes(ctx -> startBattle(ctx.getSource(), DEFAULT_DECK, Difficulty.EASY)))
                        .then(Commands.literal("normal")
                                .executes(ctx -> startBattle(ctx.getSource(), DEFAULT_DECK, Difficulty.NORMAL)))
                        .then(Commands.literal("hard")
                                .executes(ctx -> startBattle(ctx.getSource(), DEFAULT_DECK, Difficulty.HARD)))
                        .then(Commands.argument("deck_size", IntegerArgumentType.integer(4, 80))
                                .executes(ctx -> startBattle(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "deck_size"), Difficulty.NORMAL))
                                .then(Commands.literal("easy")
                                        .executes(ctx -> startBattle(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "deck_size"), Difficulty.EASY)))
                                .then(Commands.literal("normal")
                                        .executes(ctx -> startBattle(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "deck_size"), Difficulty.NORMAL)))
                                .then(Commands.literal("hard")
                                        .executes(ctx -> startBattle(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "deck_size"), Difficulty.HARD)))))
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
                                        EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.literal("wager")
                                        .executes(ctx -> DuelManager.challenge(
                                                ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"), true)))
                                .then(Commands.literal("bet")
                                        .then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 4096))
                                                .executes(ctx -> DuelManager.challengeBet(
                                                        ctx.getSource().getPlayerOrException(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "emeralds")))))))
                .then(Commands.literal("trade")
                        .then(Commands.literal("accept")
                                .executes(ctx -> TradeManager.accept(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("decline")
                                .executes(ctx -> TradeManager.decline(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> TradeManager.offer(
                                        ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("queue")
                        .executes(ctx -> DuelManager.queue(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("leave")
                                .executes(ctx -> DuelManager.leaveQueue(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("rematch")
                        .executes(ctx -> DuelManager.rematch(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("watch")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> DuelManager.watch(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("unwatch")
                        .executes(ctx -> DuelManager.unwatch(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("sidebet")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 4096))
                                        .executes(ctx -> DuelManager.sideBet(
                                                ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "emeralds"))))))
                .then(Commands.literal("emote")
                        .then(Commands.argument("emote", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (String s : new String[]{"gg", "nice", "close", "oops", "gl", "wow"}) {
                                        b.suggest(s);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> DuelManager.emote(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "emote")))))
                .then(Commands.literal("foil")
                        .executes(ctx -> combineFoil(ctx.getSource())))
                .then(Commands.literal("store")
                        .executes(ctx -> BinderStorage.depositAll(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("withdraw")
                        .executes(ctx -> BinderStorage.withdrawAll(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("deck")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deckSave(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            try {
                                                DeckManager.slots(ctx.getSource().getPlayerOrException())
                                                        .keySet().forEach(b::suggest);
                                            } catch (CommandSyntaxException ignored) {
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> deckLoad(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deckDelete(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> deckList(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("export")
                        .executes(ctx -> exportDeck(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("import")
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(ctx -> importDeck(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "code")))))
                .then(Commands.literal("guide")
                        .executes(ctx -> GuideBook.give(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("draft")
                        .then(Commands.literal("accept")
                                .executes(ctx -> DraftManager.accept(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("decline")
                                .executes(ctx -> DraftManager.decline(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> DraftManager.invite(
                                        ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("pick")
                        .then(Commands.argument("mob_id", StringArgumentType.word())
                                .executes(ctx -> DraftManager.pick(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "mob_id")))))
                .then(Commands.literal("stats")
                        .executes(ctx -> StatsTracker.dashboard(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("profile")
                        .executes(ctx -> StatsTracker.profile(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("quests")
                        .executes(ctx -> QuestManager.show(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("quest")
                        .then(Commands.literal("claim")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, 2))
                                        .executes(ctx -> QuestManager.claim(
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "slot"))))))
                .then(Commands.literal("tournament")
                        .then(Commands.literal("open")
                                .then(Commands.argument("fee", IntegerArgumentType.integer(0, 1024))
                                        .executes(ctx -> TournamentManager.open(
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "fee")))))
                        .then(Commands.literal("join")
                                .executes(ctx -> TournamentManager.join(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("start")
                                .executes(ctx -> TournamentManager.start(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("status")
                                .executes(ctx -> TournamentManager.status(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("top")
                        .executes(ctx -> leaderboard(ctx.getSource()))));
    }

    private static int leaderboard(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Leaderboard board = Leaderboard.get(player.serverLevel().getServer());
        var top = board.top(10);
        player.sendSystemMessage(Component.literal("═══ MOB TRUMPS · RANKED ═══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (top.isEmpty()) {
            player.sendSystemMessage(Component.literal("No duels played yet. Challenge someone!")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }
        int rank = 1;
        for (Leaderboard.Entry e : top) {
            ChatFormatting color = rank == 1 ? ChatFormatting.GOLD
                    : rank == 2 ? ChatFormatting.GRAY : rank == 3 ? ChatFormatting.DARK_RED
                    : ChatFormatting.WHITE;
            player.sendSystemMessage(Component.literal(String.format(" %2d. ", rank))
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(e.name()).withStyle(color))
                    .append(Component.literal("  " + e.rating()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  (" + e.wins() + "W " + e.losses() + "L)")
                            .withStyle(ChatFormatting.DARK_GRAY)));
            rank++;
        }
        int myRank = board.rankOf(player.getUUID());
        Leaderboard.Entry me = board.entry(player.getUUID());
        if (me != null) {
            player.sendSystemMessage(Component.literal("You: rank #" + myRank + " · " + me.rating()
                            + " (" + me.wins() + "W " + me.losses() + "L)")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    // --- command handlers ---

    private static final int FOIL_COST = 4;

    private static int combineFoil(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var held = player.getMainHandItem();
        var card = MobCardItem.cardOf(held);
        if (card == null || MobCardItem.isFoilCard(held)) {
            player.sendSystemMessage(Component.literal(
                            "Hold a non-foil mob card. " + FOIL_COST + " copies combine into 1 holographic foil.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int have = CardActions.count(player, card.id(), false);
        if (have < FOIL_COST) {
            player.sendSystemMessage(Component.literal("You need " + FOIL_COST + " copies of "
                            + card.displayName() + " to press a foil (you have " + have + ").")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        CardActions.remove(player, card.id(), false, FOIL_COST);
        var foil = MobCardItem.stackOf(card, true);
        CardActions.give(player, foil);
        CollectionTracker.record(player, card.id(), true);
        player.sendSystemMessage(Component.literal("✦ Pressed " + FOIL_COST + " " + card.displayName()
                        + " cards into a holographic foil! ✦").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.3F);
        return 1;
    }

    private static int deckSave(ServerPlayer player, String name) {
        if (DeckManager.saveSlot(player, name)) {
            player.sendSystemMessage(Component.literal("Saved your active deck as \"" + name + "\".")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        }
        player.sendSystemMessage(Component.literal("Couldn't save: your active deck is empty, the name is "
                        + "invalid, or you already have " + DeckManager.MAX_SLOTS + " saved decks.")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int deckLoad(ServerPlayer player, String name) {
        if (DeckManager.loadSlot(player, name)) {
            int size = DeckManager.deckCards(player).size();
            player.sendSystemMessage(Component.literal("Loaded deck \"" + name + "\" ("
                            + size + " cards you own).").withStyle(ChatFormatting.GREEN));
            return 1;
        }
        player.sendSystemMessage(Component.literal("No saved deck called \"" + name + "\".")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int deckDelete(ServerPlayer player, String name) {
        if (DeckManager.deleteSlot(player, name)) {
            player.sendSystemMessage(Component.literal("Deleted deck \"" + name + "\".")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }
        player.sendSystemMessage(Component.literal("No saved deck called \"" + name + "\".")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int deckList(ServerPlayer player) {
        var slots = DeckManager.slots(player);
        if (slots.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                            "No saved decks. Build one in the Collection Book, then /mobtrumps deck save <name>.")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }
        player.sendSystemMessage(Component.literal("Saved decks (" + slots.size() + "/"
                + DeckManager.MAX_SLOTS + "):").withStyle(ChatFormatting.GOLD));
        for (var e : slots.entrySet()) {
            player.sendSystemMessage(Component.literal("  " + e.getKey() + " — " + e.getValue().size()
                            + " cards ").withStyle(ChatFormatting.GRAY)
                    .append(button("[Load]", "/mobtrumps deck load " + e.getKey(),
                            ChatFormatting.GREEN, "Make this your active deck"))
                    .append(Component.literal(" "))
                    .append(button("[X]", "/mobtrumps deck delete " + e.getKey(),
                            ChatFormatting.RED, "Delete this deck")));
        }
        return 1;
    }

    private static int exportDeck(ServerPlayer player) {
        var deck = player.getData(ModAttachments.DECK.get());
        if (deck.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                            "Your deck is empty — build one in the Collection Book first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String code = DeckCodes.encode(deck);
        player.sendSystemMessage(Component.literal("Deck code (click to copy): ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(code).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Copy to clipboard"))))));
        player.sendSystemMessage(Component.literal("Share it; a friend imports with /mobtrumps import <code>")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int importDeck(ServerPlayer player, String code) {
        var ids = DeckCodes.decode(code);
        if (ids == null || ids.isEmpty()) {
            player.sendSystemMessage(Component.literal("That deck code is invalid.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        DeckManager.saveDeck(player, ids);
        int owned = DeckManager.deckCards(player).size();
        player.sendSystemMessage(Component.literal("Imported deck — " + owned + " of " + ids.size()
                        + " cards are ones you own and are now in your deck.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int menu(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ MOB TRUMPS ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Collect all 81 mob cards and battle with them.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  ")
                .append(button("[Battle the CPU]", "/mobtrumps battle", ChatFormatting.GREEN,
                        "Start a solo Top Trumps battle"))
                .append(Component.literal("  "))
                .append(button("[Forfeit]", "/mobtrumps forfeit", ChatFormatting.RED,
                        "Give up your current game")));
        player.sendSystemMessage(Component.literal("  Duel a friend: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobtrumps duel <player>").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" — add ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("wager").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (stake a card) or ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("bet <emeralds>").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (winner takes the pot)").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  ")
                .append(button("[Quick match]", "/mobtrumps queue", ChatFormatting.GREEN,
                        "Auto-match against another waiting player"))
                .append(Component.literal("  ·  Spectate: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("/mobtrumps watch <player>").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (with side bets)").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  Collect cards by hunting: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("every mob you kill drops its card. Kill enough of one "
                                + "mob to unlock its boosted holographic!")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  Trade / combine: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobtrumps trade <player>").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("/mobtrumps foil").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (4 dupes → 1 foil)").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  Build a deck: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Collection Book → Deck, then /mobtrumps battle deck")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  ")
                .append(button("[Daily quests]", "/mobtrumps quests", ChatFormatting.GREEN,
                        "Three fresh challenges every day, paid in emeralds"))
                .append(Component.literal("  "))
                .append(button("[Tournament]", "/mobtrumps tournament status", ChatFormatting.GOLD,
                        "Server-wide bracket — winner takes the pot")));
        player.sendSystemMessage(Component.literal("  Draft mode: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobtrumps draft <player>").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" — take turns picking a shared pool, then duel")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  ")
                .append(button("[My stats]", "/mobtrumps stats", ChatFormatting.AQUA,
                        "Your win rate, favourite stat and nemesis"))
                .append(Component.literal("  "))
                .append(button("[My profile card]", "/mobtrumps profile", ChatFormatting.LIGHT_PURPLE,
                        "You, as a Top Trumps card"))
                .append(Component.literal("  ·  Deck slots: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("/mobtrumps deck save|load|list").withStyle(ChatFormatting.AQUA)));
        player.sendSystemMessage(Component.literal("  CPU difficulty: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobtrumps battle easy|normal|hard").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  ·  Share decks: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("/mobtrumps export").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("import <code>").withStyle(ChatFormatting.AQUA)));
        player.sendSystemMessage(Component.literal("  Tidy up: ")
                .withStyle(ChatFormatting.GRAY)
                .append(button("[Store cards]", "/mobtrumps store", ChatFormatting.AQUA,
                        "File one of each loose card into your Collection Book"))
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("/mobtrumps withdraw").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" empties the book back out").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("  ")
                .append(button("[Ranked leaderboard]", "/mobtrumps top", ChatFormatting.GOLD,
                        "See the server's top duelists"))
                .append(Component.literal("  "))
                .append(button("[How to play]", "/mobtrumps guide", ChatFormatting.AQUA,
                        "Get the Mob Trumps guide book")));
        player.sendSystemMessage(Component.literal("  Craft a Collection Book (book + emerald) to "
                        + "track and store your cards.")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int startBattle(CommandSourceStack source, int deckSize, Difficulty difficulty)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Battle battle = new Battle(deckSize, ThreadLocalRandom.current());
        battle.setDifficulty(difficulty);
        BATTLES.put(player.getUUID(), battle);

        player.sendSystemMessage(Component.literal("=== MOB TRUMPS · " + difficulty.label() + " CPU ===")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(
                        "The deck is dealt: " + battle.playerCardCount() + " cards each. "
                        + "Higher stat wins the round; win every card to win the battle!")
                .withStyle(ChatFormatting.GRAY));
        shuffleSound(player);
        promptRound(player, battle);
        return 1;
    }

    private static int startDeckBattle(CommandSourceStack source, Difficulty difficulty)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var deck = DeckManager.deckCards(player);
        if (deck.size() < DeckManager.MIN_DECK) {
            player.sendSystemMessage(Component.literal("Build a deck of at least "
                            + DeckManager.MIN_DECK + " cards first (open your Collection Book → Deck).")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Battle battle = new Battle(deck, java.util.List.of(), ThreadLocalRandom.current());
        battle.setDifficulty(difficulty);
        BATTLES.put(player.getUUID(), battle);
        player.sendSystemMessage(Component.literal("=== MOB TRUMPS: YOUR DECK · " + difficulty.label()
                        + " CPU ===").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Your " + battle.playerCardCount()
                        + "-card deck vs a random CPU deck. Higher stat wins!")
                .withStyle(ChatFormatting.GRAY));
        shuffleSound(player);
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
        StatsTracker.recordPick(player, stat);
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
            case PLAYER -> {
                player.sendSystemMessage(Component.literal("You take the round!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                roundSound(player, 1.3F);
            }
            case CPU -> {
                player.sendSystemMessage(Component.literal("The CPU takes the round.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                roundSound(player, 0.7F);
            }
            case NONE -> {
                player.sendSystemMessage(Component.literal("Tie! Both cards go into the pot.")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                roundSound(player, 1.0F);
            }
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
                StatsTracker.bump(player, "battle_wins");
                ItemStack reward = new ItemStack(net.minecraft.world.item.Items.EMERALD, 3);
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
                player.sendSystemMessage(Component.literal("Reward: 3 emeralds")
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

    private static void roundSound(ServerPlayer player, float pitch) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, pitch);
    }

    /** A quick riffle of paper "flicks" to sell the deck being shuffled and dealt. */
    static void shuffleSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 0.9F);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.2F);
        player.playNotifySound(SoundEvents.BAMBOO_HIT, SoundSource.PLAYERS, 0.4F, 1.6F);
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
