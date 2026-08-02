package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.GuessQuestion;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guess Who: one hidden mob, eighty-one faces, and a fixed list of questions.
 *
 * <p>Two ways to play. <b>Against a player</b> is the real game: each of you
 * picks the mob the other has to find, then you take turns asking. <b>Against
 * the house</b> hides a random one, for when nobody else is about.
 *
 * <p><b>The elimination happens here, not on the client.</b> That is the whole
 * design. A question arrives as "template 4, value 7", the server answers it
 * against the mob being hunted, strikes off everything that would have answered
 * the other way, and sends back what is left. The client draws that and nothing
 * else. So a player cannot decline to eliminate a face they would rather keep,
 * cannot miscount in their own favour, and a modified client cannot ask what the
 * mob is — the only reply it ever receives is a yes, a no, and a shorter list.
 *
 * <p>Choosing your opponent's mob is safe now that traits exist. Before them,
 * Skeleton and Zombie, Bogged and Stray, and Cow and Sheep were identical on
 * every stat and category, so a free choice meant everyone picked one of those
 * six and every game ended in a coin flip. All 81 are separable now, and the
 * spread between the easiest and hardest to corner is one question.
 */
public final class GuessWhoManager {

    public static final int PHASE_PICKING = 0;
    public static final int PHASE_WAITING = 1;
    public static final int PHASE_YOUR_TURN = 2;
    public static final int PHASE_THEIR_TURN = 3;
    public static final int PHASE_WON = 4;
    public static final int PHASE_LOST = 5;

    /** How long an unanswered challenge stands. */
    private static final long INVITE_MS = 60_000L;

    private record Asked(int template, int value, boolean answer) {
    }

    /** One player's hunt: the mob they are looking for and what is still standing. */
    private static final class Board {
        MobCard secret;
        final List<MobCard> alive = new ArrayList<>(MobCards.ALL);
        final List<Asked> log = new ArrayList<>();
    }

    /**
     * A game. Solo has one side; a match has two, each hunting the mob the other
     * chose, taking turns.
     */
    private static final class Game {
        final UUID a;
        final UUID b;              // null for a solo board
        final Board boardA = new Board();
        final Board boardB = new Board();
        UUID turn;
        UUID winner;
        boolean done;

        Game(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        boolean solo() {
            return b == null;
        }

        Board boardFor(UUID id) {
            return id.equals(a) ? boardA : boardB;
        }

        UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }

        /** Both sides have chosen what the other must find. */
        boolean ready() {
            return boardA.secret != null && boardB.secret != null;
        }
    }

    private static final Map<UUID, Game> GAMES = new ConcurrentHashMap<>();
    /** target -> (challenger, expiry) for a pending invitation. */
    private static final Map<UUID, Object[]> INVITES = new ConcurrentHashMap<>();

    private GuessWhoManager() {
    }

    // --- starting -----------------------------------------------------------

    /** Solo: the house hides one and you hunt it. */
    public static void openSolo(ServerPlayer player) {
        Game game = new Game(player.getUUID(), null);
        game.boardA.secret = MobCards.ALL.get(
                ThreadLocalRandom.current().nextInt(MobCards.ALL.size()));
        game.turn = player.getUUID();
        GAMES.put(player.getUUID(), game);
        PacketDistributor.sendToPlayer(player, new GuessWhoMenuPayload());
        send(player);
    }

    /** Challenge another player. They pick your mob and you pick theirs. */
    public static int challenge(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            from.sendSystemMessage(err("You cannot play yourself."));
            return 0;
        }
        if (inGame(from) || inGame(to)) {
            from.sendSystemMessage(err("One of you is already in a game."));
            return 0;
        }
        INVITES.put(to.getUUID(), new Object[]{from.getUUID(),
                System.currentTimeMillis() + INVITE_MS});
        from.sendSystemMessage(Component.literal("Challenge sent to " + name(to) + ".")
                .withStyle(ChatFormatting.GREEN));
        to.sendSystemMessage(Component.literal(name(from) + " challenges you at Guess Who. ")
                .withStyle(ChatFormatting.GOLD)
                .append(BattleCommands.button("[Accept]", "/mobtrumps guesswho accept",
                        ChatFormatting.GREEN, "Each of you picks the other's mob"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps guesswho decline",
                        ChatFormatting.RED, "Turn it down")));
        return 1;
    }

    public static int accept(ServerPlayer target) {
        Object[] invite = INVITES.remove(target.getUUID());
        if (invite == null || (Long) invite[1] < System.currentTimeMillis()) {
            target.sendSystemMessage(err("No challenge waiting."));
            return 0;
        }
        ServerPlayer from = target.getServer() == null ? null
                : target.getServer().getPlayerList().getPlayer((UUID) invite[0]);
        if (from == null || inGame(from) || inGame(target)) {
            target.sendSystemMessage(err("That challenge has lapsed."));
            return 0;
        }
        Game game = new Game(from.getUUID(), target.getUUID());
        GAMES.put(from.getUUID(), game);
        GAMES.put(target.getUUID(), game);
        for (ServerPlayer p : new ServerPlayer[]{from, target}) {
            PacketDistributor.sendToPlayer(p, new GuessWhoMenuPayload());
            p.sendSystemMessage(Component.literal(
                            "Guess Who — pick the mob your opponent has to find.")
                    .withStyle(ChatFormatting.GOLD));
            send(p);
        }
        return 1;
    }

    public static int decline(ServerPlayer target) {
        Object[] invite = INVITES.remove(target.getUUID());
        if (invite != null && target.getServer() != null) {
            ServerPlayer from = target.getServer().getPlayerList().getPlayer((UUID) invite[0]);
            if (from != null) {
                from.sendSystemMessage(Component.literal(name(target) + " declined.")
                        .withStyle(ChatFormatting.RED));
            }
        }
        return 1;
    }

    public static boolean inGame(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        return game != null && !game.done;
    }

    // --- playing --------------------------------------------------------------

    public static void handle(ServerPlayer player, int action, int template, int value,
                              String mobId) {
        switch (action) {
            case GuessWhoActionPayload.NEW_GAME -> openSolo(player);
            case GuessWhoActionPayload.ASK -> ask(player, template, value);
            case GuessWhoActionPayload.GUESS -> guess(player, mobId);
            case GuessWhoActionPayload.PICK -> pick(player, mobId);
            case GuessWhoActionPayload.LEAVE -> quit(player);
            default -> {
            }
        }
    }

    /**
     * Choose the mob your opponent must find. Any of the 81 — every one of them
     * can be cornered, so there is no hiding place to abuse.
     */
    private static void pick(ServerPlayer player, String mobId) {
        Game game = GAMES.get(player.getUUID());
        if (game == null || game.done || game.solo()) {
            return;
        }
        MobCard chosen = MobCards.byId(mobId);
        UUID them = game.other(player.getUUID());
        if (chosen == null || them == null) {
            return;
        }
        Board theirBoard = game.boardFor(them);
        if (theirBoard.secret != null) {
            return; // already chosen; a pick cannot be changed once made
        }
        theirBoard.secret = chosen;
        player.sendSystemMessage(Component.literal(
                        "You hid " + chosen.displayName() + " for them to find.")
                .withStyle(ChatFormatting.GREEN));
        if (game.ready()) {
            // the challenger moves first, as in any duel
            game.turn = game.a;
        }
        sendBoth(player, game);
    }

    private static void ask(ServerPlayer player, int templateIndex, int value) {
        Game game = GAMES.get(player.getUUID());
        if (game == null || game.done || !BlockReach.canReach(player)) {
            return;
        }
        if (!game.solo() && (!game.ready() || !player.getUUID().equals(game.turn))) {
            return; // not your turn, or somebody is still choosing
        }
        List<GuessQuestion.Template> all = GuessQuestion.TEMPLATES;
        if (templateIndex < 0 || templateIndex >= all.size()) {
            return;
        }
        Board board = game.boardFor(player.getUUID());
        if (board.secret == null) {
            return;
        }
        GuessQuestion.Template template = all.get(templateIndex);
        int v = template.needsValue() ? template.clamp(value) : 0;
        if (!GuessQuestion.isUseful(board.alive, template, v)) {
            player.sendSystemMessage(Component.literal(
                            "Every face left answers that the same way — ask something else.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean answer = GuessQuestion.ask(template, v, board.secret);
        int before = board.alive.size();
        List<MobCard> left = GuessQuestion.eliminate(board.alive, template, v, answer);
        board.alive.clear();
        board.alive.addAll(left);
        board.log.add(new Asked(templateIndex, v, answer));
        sound(player, answer ? SoundEvents.NOTE_BLOCK_BELL.value()
                : SoundEvents.NOTE_BLOCK_BASS.value(), answer ? 1.4F : 0.8F);
        player.sendSystemMessage(Component.literal(
                        (answer ? "YES" : "NO") + " — " + template.text(v)
                                + "  (" + before + " → " + board.alive.size() + " left)")
                .withStyle(answer ? ChatFormatting.GREEN : ChatFormatting.RED));
        passTurn(game);
        sendBoth(player, game);
    }

    private static void guess(ServerPlayer player, String mobId) {
        Game game = GAMES.get(player.getUUID());
        if (game == null || game.done || !BlockReach.canReach(player)) {
            return;
        }
        if (!game.solo() && (!game.ready() || !player.getUUID().equals(game.turn))) {
            return;
        }
        Board board = game.boardFor(player.getUUID());
        MobCard named = MobCards.byId(mobId);
        if (named == null || board.secret == null) {
            return;
        }
        if (board.alive.stream().noneMatch(c -> c.id().equals(named.id()))) {
            return; // a face already struck off is a misclick, not a move
        }
        boolean right = named.id().equals(board.secret.id());
        StatsTracker.bump(player, "guesswho_played");
        if (right) {
            game.winner = player.getUUID();
            StatsTracker.bump(player, "guesswho_wins");
            ServerSync.markAwards(player);
            RecyclerManager.giveFragments(player, reward(board.log.size()));
            player.sendSystemMessage(Component.literal(
                            "Guess Who: " + named.displayName() + ", in " + board.log.size()
                                    + " question" + (board.log.size() == 1 ? "" : "s") + " — "
                                    + reward(board.log.size()) + " fragments.")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            game.winner = game.solo() ? null : game.other(player.getUUID());
            player.sendSystemMessage(Component.literal(
                            "Guess Who: not " + named.displayName() + " — it was "
                                    + board.secret.displayName() + ".")
                    .withStyle(ChatFormatting.RED));
        }
        game.done = true;
        sound(player, right ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_BREAK,
                right ? 1.2F : 0.8F);
        if (!game.solo()) {
            ServerPlayer them = playerOf(player, game.other(player.getUUID()));
            if (them != null) {
                them.sendSystemMessage(Component.literal(right
                                ? name(player) + " found your " + board.secret.displayName()
                                        + " in " + board.log.size() + "."
                                : name(player) + " guessed wrong — you win.")
                        .withStyle(right ? ChatFormatting.RED : ChatFormatting.GREEN));
                if (!right) {
                    RecyclerManager.giveFragments(them, reward(12));
                }
            }
        }
        sendBoth(player, game);
    }

    /** In a match, asking or naming hands the turn over. */
    private static void passTurn(Game game) {
        if (!game.solo() && game.ready() && game.turn != null) {
            game.turn = game.other(game.turn);
        }
    }

    private static void quit(ServerPlayer player) {
        Game game = GAMES.remove(player.getUUID());
        if (game == null || game.solo()) {
            return;
        }
        UUID them = game.other(player.getUUID());
        GAMES.remove(them);
        if (!game.done) {
            ServerPlayer other = playerOf(player, them);
            if (other != null) {
                other.sendSystemMessage(Component.literal(name(player) + " left the game.")
                        .withStyle(ChatFormatting.GRAY));
                send(other);
            }
        }
    }

    /**
     * What finding it pays. The floor is log2(81) ≈ 6.34 questions and a careful
     * player needs six or seven, so the top rung is for a genuinely sharp game
     * without making an ordinary one feel like a failure.
     */
    public static int reward(int questions) {
        if (questions <= 5) {
            return 24;
        }
        if (questions <= 7) {
            return 12;
        }
        if (questions <= 10) {
            return 6;
        }
        return 2;
    }

    public static void handleLogout(ServerPlayer player) {
        INVITES.remove(player.getUUID());
        quit(player);
        GAMES.remove(player.getUUID());
    }

    // --- wire format ------------------------------------------------------------

    private static ServerPlayer playerOf(ServerPlayer near, UUID id) {
        return id == null || near.getServer() == null ? null
                : near.getServer().getPlayerList().getPlayer(id);
    }

    private static void sendBoth(ServerPlayer player, Game game) {
        send(player);
        if (!game.solo()) {
            ServerPlayer them = playerOf(player, game.other(player.getUUID()));
            if (them != null) {
                send(them);
            }
        }
    }

    private static int phaseFor(Game game, UUID who) {
        if (game.done) {
            if (game.solo()) {
                return game.winner != null ? PHASE_WON : PHASE_LOST;
            }
            return who.equals(game.winner) ? PHASE_WON : PHASE_LOST;
        }
        if (!game.solo()) {
            UUID them = game.other(who);
            if (game.boardFor(them).secret == null) {
                return PHASE_PICKING;   // you still owe them a mob
            }
            if (!game.ready()) {
                return PHASE_WAITING;   // you have picked; they have not
            }
            return who.equals(game.turn) ? PHASE_YOUR_TURN : PHASE_THEIR_TURN;
        }
        return PHASE_YOUR_TURN;
    }

    private static void send(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        if (game == null) {
            return;
        }
        UUID me = player.getUUID();
        Board board = game.boardFor(me);
        int phase = phaseFor(game, me);

        List<String> alive = new ArrayList<>();
        // before both have picked there is nothing to hunt yet, so the board
        // shows every face — that is the pool you are choosing from
        for (MobCard card : board.secret == null ? MobCards.ALL : board.alive) {
            alive.add(card.id());
        }
        List<String> log = new ArrayList<>(board.log.size());
        for (Asked asked : board.log) {
            log.add(asked.template() + "|" + asked.value() + "|" + (asked.answer() ? 1 : 0));
        }
        // the hidden mob crosses the wire only once the game is settled
        String secret = game.done && board.secret != null ? board.secret.id() : "";

        String opponent = "";
        if (!game.solo()) {
            UUID them = game.other(me);
            ServerPlayer other = playerOf(player, them);
            Board theirs = game.boardFor(them);
            opponent = (other == null ? "?" : name(other)) + "|"
                    + (theirs.secret == null ? MobCards.ALL.size() : theirs.alive.size()) + "|"
                    + theirs.log.size();
        }
        PacketDistributor.sendToPlayer(player, new GuessWhoSyncPayload(
                phase, board.log.size(), alive, log, secret, opponent));
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event,
                              float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundSource.PLAYERS, 0.7F, pitch);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
