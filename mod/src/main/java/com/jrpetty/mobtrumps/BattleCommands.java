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
import net.neoforged.neoforge.event.RegisterCommandsEvent;


/**
 * The /mobtrumps command: information, collection housekeeping and a few
 * ways to find an opponent by name.
 *
 * <p>No game is played through chat. Every one of them — against the CPU, on
 * a campaign mission, in a duel, a draft or a parlour game — starts at a
 * Dueling Table and is played on its own screen, so nothing this command
 * prints is a button: what is left in chat is information.
 *
 *   /mobtrumps duel <player>       - challenge another player (answered on screen)
 *   /mobtrumps queue               - wait to be matched with anyone
 *   /mobtrumps watch <player>      - spectate a duel
 */
public final class BattleCommands {

    private BattleCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mobtrumps")
                .executes(ctx -> menu(ctx.getSource().getPlayerOrException()))
                // every game is played at a Dueling Table now; the old chat
                // battle is gone, so the word only points the way there
                .then(Commands.literal("battle")
                        .executes(ctx -> tableOnly(ctx.getSource().getPlayerOrException())))
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
                                                        IntegerArgumentType.getInteger(ctx, "emeralds")))))
                                .then(Commands.literal("bo3")
                                        .executes(ctx -> DuelManager.challenge(
                                                ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"), false, 3)))
                                .then(Commands.literal("bo5")
                                        .executes(ctx -> DuelManager.challenge(
                                                ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"), false, 5)))))
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
                .then(Commands.literal("categories")
                        .executes(ctx -> categories(ctx.getSource().getPlayerOrException())))
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
                        .executes(ctx -> leaderboard(ctx.getSource())))
                .then(Commands.literal("record")
                        .executes(ctx -> MatchHistory.print(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("ranked")
                        .executes(ctx -> {
                            RankedStandings.send(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("season")
                        .executes(ctx -> season(ctx.getSource()))
                        .then(Commands.literal("end")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    Leaderboard.get(p.serverLevel().getServer())
                                            .endSeason(p.serverLevel().getServer());
                                    return 1;
                                }))));
    }

    private static int categories(ServerPlayer player) {
        var collected = player.getData(ModAttachments.COLLECTED.get());
        player.sendSystemMessage(Component.literal("═══ MOB CATEGORIES ═══")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        int done = 0;
        for (com.jrpetty.mobtrumps.game.Category cat : com.jrpetty.mobtrumps.game.Category.values()) {
            var members = com.jrpetty.mobtrumps.game.MobCategories.members(cat);
            int have = 0;
            for (String id : members) if (collected.contains(id)) have++;
            boolean complete = have == members.size();
            boolean claimed = CategoryRewards.isClaimed(player, cat);
            if (complete) done++;

            var reward = com.jrpetty.mobtrumps.game.CategoryReward.of(cat);
            String stars = "★".repeat(cat.difficulty());
            String status = claimed ? "  ✔ claimed" : complete ? "  ✦ COMPLETE!" : "";
            MutableComponent line = Component.literal(String.format("  %-19s ", cat.label()))
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(
                            net.minecraft.network.chat.TextColor.fromRgb(cat.accent() & 0xFFFFFF)));
            line.append(Component.literal(have + "/" + members.size() + " " + stars)
                    .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (!status.isEmpty()) {
                line.append(Component.literal(status).withStyle(
                        claimed ? ChatFormatting.DARK_GREEN : ChatFormatting.GOLD));
            }
            player.sendSystemMessage(line);
            String armor = reward.armor().name().charAt(0)
                    + reward.armor().name().substring(1).toLowerCase(java.util.Locale.ROOT);
            player.sendSystemMessage(Component.literal("      Reward: " + reward.diamond()
                            + " Diamonds, " + reward.iron() + " Iron, " + reward.gold()
                            + " Gold, + a random enchanted " + armor + " piece")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        player.sendSystemMessage(Component.literal("  Completed " + done + " / "
                        + com.jrpetty.mobtrumps.game.Category.values().length + " categories.")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int season(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Leaderboard board = Leaderboard.get(player.serverLevel().getServer());
        player.sendSystemMessage(Component.literal("═══ RANKED · SEASON " + board.season() + " ═══")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  Ends in " + StatsTracker.humanDuration(board.msLeft())
                        + " — final tier earns a badge + emerald payout.")
                .withStyle(ChatFormatting.GRAY));
        StatsTracker.rankedSection(player, board);
        player.sendSystemMessage(Component.literal("  The standings: ").withStyle(ChatFormatting.GRAY)
                .append(typed("/mobtrumps top")));
        return 1;
    }

    private static int leaderboard(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Leaderboard board = Leaderboard.get(player.serverLevel().getServer());
        var top = board.top(10);
        player.sendSystemMessage(Component.literal("═══ RANKED · SEASON " + board.season()
                        + " · " + StatsTracker.humanDuration(board.msLeft()) + " left ═══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (top.isEmpty()) {
            player.sendSystemMessage(Component.literal("No duels played yet. Challenge someone!")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }
        int rank = 1;
        for (Leaderboard.Entry e : top) {
            ChatFormatting place = rank == 1 ? ChatFormatting.GOLD
                    : rank == 2 ? ChatFormatting.GRAY : rank == 3 ? ChatFormatting.DARK_RED
                    : ChatFormatting.WHITE;
            player.sendSystemMessage(Component.literal(String.format(" %2d. ", rank))
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(e.name()).withStyle(place))
                    .append(Component.literal("  " + RankTier.label(e.rating()))
                            .withStyle(RankTier.of(e.rating()).color))
                    .append(Component.literal("  " + e.rating()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  (" + e.wins() + "W " + e.losses() + "L)")
                            .withStyle(ChatFormatting.DARK_GRAY)));
            rank++;
        }
        int myRank = board.rankOf(player.getUUID());
        Leaderboard.Entry me = board.entry(player.getUUID());
        if (me != null) {
            player.sendSystemMessage(Component.literal("You: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(RankTier.label(me.rating()))
                            .withStyle(RankTier.of(me.rating()).color, ChatFormatting.BOLD))
                    .append(Component.literal("  rank #" + myRank + " · " + me.rating()
                            + " (" + me.wins() + "W " + me.losses() + "L)")
                            .withStyle(ChatFormatting.GREEN)));
        } else {
            player.sendSystemMessage(Component.literal("You're unranked — win a duel to place!")
                    .withStyle(ChatFormatting.GRAY));
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
                    + " cards").withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal("  Load one: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(typed("/mobtrumps deck load <name>"))
                .append(Component.literal("  ·  delete: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(typed("/mobtrumps deck delete <name>")));
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

    /**
     * {@code /mobtrumps} on its own: what the mod is and where things are.
     * Information only — every game starts at a Dueling Table, so nothing here
     * is a button to click.
     */
    private static int menu(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ MOB TRUMPS ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Collect all 81 mob cards and play them at a Dueling Table.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(line("Play: ", "right-click a Dueling Table — the CPU, the campaign, "
                + "duels, drafts, Memory, Guess Who, Mob Bluff and Twenty-One all start there."));
        player.sendSystemMessage(line("Collect: ", "every mob you kill can drop its card; hunt one mob "
                + "enough and its holographic unlocks."));
        player.sendSystemMessage(line("Your deck: ", Battle.HAND_SIZE + " cards — build it in the "
                + "Collection Book, or from the table's Edit Deck."));
        player.sendSystemMessage(line("Every finished game ", "pays experience — win, lose or draw."));
        player.sendSystemMessage(Component.literal("  Commands: ").withStyle(ChatFormatting.GRAY)
                .append(typed("guide · stats · profile · quests · top · season · record · foil · "
                        + "store · withdraw · deck save|load|list · export · import <code>")));
        player.sendSystemMessage(Component.literal("  By name: ").withStyle(ChatFormatting.GRAY)
                .append(typed("/mobtrumps duel <player> [wager | bet <emeralds> | bo3 | bo5]"))
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(typed("queue"))
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(typed("watch <player>")));
        return 1;
    }

    private static Component line(String head, String body) {
        return Component.literal("  " + head).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(body).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Where every game is played now. */
    private static int tableOnly(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Mob Trumps is played at a Dueling Table — "
                        + "right-click one for the CPU, the campaign, duels, drafts and every other game.")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    /**
     * The deck being riffled and dealt.
     *
     * <p>This was three vanilla samples stacked — two page turns and a bamboo
     * hit — which is a fair impression of a shuffle but carries a bookshelf
     * with it. It is one purpose-made riffle now.
     */
    static void shuffleSound(ServerPlayer player) {
        player.playNotifySound(ModSounds.SHUFFLE.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    // --- chat component helpers ---

    /**
     * A command to type, shown in chat as plain text. Chat never carries a
     * clickable button: everything that can be played is played on a screen,
     * and whatever is left in chat is information.
     */
    static Component typed(String command) {
        return Component.literal(command).withStyle(ChatFormatting.AQUA);
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
