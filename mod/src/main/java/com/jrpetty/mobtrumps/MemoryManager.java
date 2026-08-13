package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Memory;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Memory table: dealing, turns, the peek clock and the packets.
 *
 * <p>Server-authoritative in the same way Guess Who is, and for the same
 * reason: the board is the secret. The face list never leaves this side except
 * through {@code Memory.Board.faceAt}, which refuses to name a tile that is
 * still face down, so a player cannot learn the layout by reading their own
 * traffic. Every flip is re-checked here — whose turn it is, whether a peek is
 * showing, whether the tile is in range and still hidden — because the client
 * asking nicely is not evidence of anything.
 */
public final class MemoryManager {

    /** How long a missed pair stays up, so both players get to see it. */
    public static final long PEEK_MS = 1400L;
    /** How long an unanswered challenge stands. */
    private static final long INVITE_MS = 60_000L;
    /** A finished board is swept once both sides have had this long to read it. */
    private static final long LINGER_MS = 5 * 60_000L;

    private static final class Game {
        final UUID a;
        final UUID b;                    // null for a solo board
        final Memory.BoardSize size;
        final Memory.Board board;
        final long startedMs = System.currentTimeMillis();
        UUID turn;
        int scoreA;
        int scoreB;
        long peekUntilMs;
        boolean done;
        long doneMs;
        /** Set when somebody walks out, so the other side is told why. */
        UUID forfeited;

        Game(UUID a, UUID b, Memory.BoardSize size, List<String> faces) {
            this.a = a;
            this.b = b;
            this.size = size;
            this.board = new Memory.Board(faces);
            this.turn = a;
        }

        boolean solo() {
            return b == null;
        }

        UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }

        int scoreOf(UUID id) {
            return id.equals(a) ? scoreA : scoreB;
        }

        void award(UUID id) {
            if (id.equals(a)) {
                scoreA++;
            } else {
                scoreB++;
            }
        }
    }

    private static final Map<UUID, Game> GAMES = new ConcurrentHashMap<>();
    /** What board size each player last chose, remembered between rounds. */
    private static final Map<UUID, Integer> SIZE_CHOICE = new ConcurrentHashMap<>();
    /** target -> (challenger, board size, expiry) for a pending invitation. */
    private static final Map<UUID, Object[]> INVITES = new ConcurrentHashMap<>();

    private MemoryManager() {
    }

    // --- starting -----------------------------------------------------------

    /** Open the table. No board is dealt until a size is chosen. */
    public static void open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MemoryMenuPayload(0));
        send(player);
    }

    public static boolean inGame(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        return game != null && !game.done;
    }

    private static Memory.BoardSize sizeOf(ServerPlayer player) {
        return Memory.BoardSize.byOrdinal(
                SIZE_CHOICE.getOrDefault(player.getUUID(), 0));
    }

    /**
     * The mobs a board may be dealt from.
     *
     * <p>Your own collection, so the board is made of cards you have actually
     * found — falling back to the full set when you have not found enough yet,
     * because a new player with four cards still deserves a game. In a match it
     * is the mobs BOTH players own, for the same reason and with the same
     * fallback: a board neither of them recognises is nobody's advantage.
     */
    private static List<String> pool(ServerPlayer one, ServerPlayer two, int pairs) {
        Set<String> mine = new LinkedHashSet<>(one.getData(ModAttachments.COLLECTED.get()));
        if (two != null) {
            mine.retainAll(new HashSet<>(two.getData(ModAttachments.COLLECTED.get())));
        }
        // only ids that are still real cards; a save from an older version can
        // name a mob the set no longer has
        List<String> owned = new ArrayList<>();
        for (String id : mine) {
            if (MobCards.byId(id) != null) {
                owned.add(id);
            }
        }
        if (owned.size() >= pairs) {
            return owned;
        }
        List<String> all = new ArrayList<>(MobCards.ALL.size());
        for (var card : MobCards.ALL) {
            all.add(card.id());
        }
        return all;
    }

    private static void startSolo(ServerPlayer player, int boardOrdinal) {
        if (inGame(player) || !BlockReach.canReach(player)) {
            return;
        }
        Memory.BoardSize size = Memory.BoardSize.byOrdinal(boardOrdinal);
        SIZE_CHOICE.put(player.getUUID(), size.ordinal());
        List<String> faces = Memory.deal(pool(player, null, size.pairs()), size.pairs(),
                new Random());
        Game game = new Game(player.getUUID(), null, size, faces);
        GAMES.put(player.getUUID(), game);
        sound(player, ModSounds.SHUFFLE.get(), 1.0F);
        send(player);
    }

    public static int challenge(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            from.sendSystemMessage(err("You cannot play yourself."));
            return 0;
        }
        if (inGame(from) || inGame(to)) {
            from.sendSystemMessage(err("One of you is already in a game."));
            return 0;
        }
        Memory.BoardSize size = sizeOf(from);
        INVITES.put(to.getUUID(), new Object[]{from.getUUID(), size.ordinal(),
                System.currentTimeMillis() + INVITE_MS});
        from.sendSystemMessage(Component.literal("Challenge sent to " + name(to) + ".")
                .withStyle(ChatFormatting.GREEN));
        to.sendSystemMessage(Component.literal(
                        name(from) + " challenges you at Memory (" + size.label + "). ")
                .withStyle(ChatFormatting.GOLD)
                .append(BattleCommands.button("[Accept]", "/mobtrumps memory accept",
                        ChatFormatting.GREEN, "Turn two cards a turn; a match keeps your turn"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps memory decline",
                        ChatFormatting.RED, "Turn it down")));
        return 1;
    }

    public static int accept(ServerPlayer target) {
        Object[] invite = INVITES.remove(target.getUUID());
        if (invite == null || (Long) invite[2] < System.currentTimeMillis()) {
            target.sendSystemMessage(err("No challenge waiting."));
            return 0;
        }
        ServerPlayer from = target.getServer() == null ? null
                : target.getServer().getPlayerList().getPlayer((UUID) invite[0]);
        if (from == null || inGame(from) || inGame(target)) {
            target.sendSystemMessage(err("That challenge has lapsed."));
            return 0;
        }
        Memory.BoardSize size = Memory.BoardSize.byOrdinal((Integer) invite[1]);
        List<String> faces = Memory.deal(pool(from, target, size.pairs()), size.pairs(),
                new Random());
        Game game = new Game(from.getUUID(), target.getUUID(), size, faces);
        GAMES.put(from.getUUID(), game);
        GAMES.put(target.getUUID(), game);
        for (ServerPlayer p : new ServerPlayer[]{from, target}) {
            PacketDistributor.sendToPlayer(p, new MemoryMenuPayload(0));
            p.sendSystemMessage(Component.literal("Memory — " + name(from) + " goes first.")
                    .withStyle(ChatFormatting.GOLD));
            sound(p, ModSounds.SHUFFLE.get(), 1.0F);
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

    // --- playing ------------------------------------------------------------

    public static void handle(ServerPlayer player, int action, int value) {
        switch (action) {
            case MemoryActionPayload.START -> startSolo(player, value);
            case MemoryActionPayload.FLIP -> flip(player, value);
            case MemoryActionPayload.SIZE -> {
                if (!inGame(player)) {
                    SIZE_CHOICE.put(player.getUUID(),
                            Memory.BoardSize.byOrdinal(value).ordinal());
                    send(player);
                }
            }
            case MemoryActionPayload.QUIT -> quit(player);
            case MemoryActionPayload.CLOSE -> clearIfDone(player);
            default -> { }
        }
    }

    /**
     * Turn a card over.
     *
     * <p>Every reason to say no is checked here rather than trusted to the
     * screen: a modified client can send any tile at any moment, and the only
     * thing standing between that and reading the whole board is this method.
     */
    private static void flip(ServerPlayer player, int tile) {
        Game game = GAMES.get(player.getUUID());
        if (game == null || game.done) {
            return;
        }
        if (!game.solo() && !player.getUUID().equals(game.turn)) {
            return;   // not your turn
        }
        if (game.peekUntilMs > 0) {
            return;   // a miss is being shown; the board is frozen
        }
        Memory.Flip result = game.board.flip(tile);
        if (result == Memory.Flip.REJECTED) {
            return;   // out of range, already turned over, or the board is done
        }
        switch (result) {
            case FIRST -> soundBoth(player, game, ModSounds.CARD_FLIP.get(), 1.0F);
            case MATCH -> {
                game.award(player.getUUID());
                soundBoth(player, game, ModSounds.CHIP.get(), 1.25F);
            }
            case MISS -> {
                game.peekUntilMs = System.currentTimeMillis() + PEEK_MS;
                soundBoth(player, game, ModSounds.CARD_FLIP.get(), 0.72F);
            }
            default -> { }
        }
        if (game.board.complete()) {
            finish(player.getServer(), game);
        }
        sendBoth(player, game);
    }

    /**
     * The peek clock, and the sweeper for boards nobody is coming back to.
     *
     * <p>Called every server tick. A game is reached through two map entries in
     * a match, so the work is done once per GAME rather than once per entry.
     */
    public static void tick(MinecraftServer server) {
        if (GAMES.isEmpty() && INVITES.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        INVITES.entrySet().removeIf(e -> (Long) e.getValue()[2] < now);

        Set<Game> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Game game : GAMES.values()) {
            if (!seen.add(game)) {
                continue;
            }
            if (game.peekUntilMs > 0 && now >= game.peekUntilMs) {
                game.peekUntilMs = 0;
                game.board.resolvePeek();
                if (!game.solo()) {
                    game.turn = game.other(game.turn);
                }
                ServerPlayer a = online(server, game.a);
                if (a != null) {
                    send(a);
                }
                ServerPlayer b = game.solo() ? null : online(server, game.b);
                if (b != null) {
                    send(b);
                }
            }
        }
        // A finished board stays put for a while so both players can read it,
        // then goes. Without this every game ever played would sit in the map
        // until the server restarted.
        GAMES.values().removeIf(g -> g.done && g.doneMs > 0 && now - g.doneMs > LINGER_MS);
    }

    private static void finish(MinecraftServer server, Game game) {
        game.done = true;
        game.doneMs = System.currentTimeMillis();
        game.peekUntilMs = 0;
        if (server == null) {
            return;
        }
        ServerPlayer a = online(server, game.a);
        ServerPlayer b = game.solo() ? null : online(server, game.b);
        if (game.solo()) {
            if (a != null) {
                a.sendSystemMessage(Component.literal("Board cleared in " + game.board.moves()
                                + " moves and " + StatsTracker.humanDuration(
                                        game.doneMs - game.startedMs) + ".")
                        .withStyle(ChatFormatting.GREEN));
                StatsTracker.bump(a, "memory_wins");
                StatsTracker.bump(a, "games_played");
                AchievementManager.refresh(a);
                win(a);
            }
            return;
        }
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p == null) {
                continue;
            }
            StatsTracker.bump(p, "memory_played");
            StatsTracker.bump(p, "games_played");
            int mine = game.scoreOf(p.getUUID());
            int theirs = game.scoreOf(game.other(p.getUUID()));
            if (mine > theirs) {
                StatsTracker.bump(p, "memory_wins");
                win(p);
            } else if (mine == theirs) {
                p.sendSystemMessage(Component.literal("Memory — a draw at " + mine + " pairs each.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            AchievementManager.refresh(p);
        }
    }

    private static void win(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Memory — you win!")
                .withStyle(ChatFormatting.GOLD));
        sound(player, SoundEvents.PLAYER_LEVELUP, 1.2F);
    }

    /** Clear a finished board so the table returns to the size menu. */
    private static void clearIfDone(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        if (game != null && game.done) {
            GAMES.remove(player.getUUID());
        }
        send(player);
    }

    private static void quit(ServerPlayer player) {
        Game game = GAMES.remove(player.getUUID());
        if (game == null) {
            return;
        }
        if (game.solo()) {
            return;
        }
        UUID them = game.other(player.getUUID());
        if (!game.done) {
            // The board dies with the player who left, and the other side is
            // told rather than being left staring at a turn that will never come.
            game.done = true;
            game.doneMs = System.currentTimeMillis();
            game.forfeited = player.getUUID();
            ServerPlayer other = online(player.getServer(), them);
            if (other != null) {
                other.sendSystemMessage(Component.literal(name(player) + " left the game.")
                        .withStyle(ChatFormatting.GRAY));
                send(other);
            }
        }
        GAMES.remove(them);
    }

    public static void handleLogout(ServerPlayer player) {
        INVITES.remove(player.getUUID());
        quit(player);
        GAMES.remove(player.getUUID());
    }

    // --- wire format --------------------------------------------------------

    private static ServerPlayer online(MinecraftServer server, UUID id) {
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static void sendBoth(ServerPlayer player, Game game) {
        send(player);
        if (!game.solo()) {
            ServerPlayer them = online(player.getServer(), game.other(player.getUUID()));
            if (them != null) {
                send(them);
            }
        }
    }

    /**
     * Send this player their view of the board.
     *
     * <p>Faces are read through {@code faceAt}, which is empty for a hidden
     * tile, so the packet contains no id the player has not earned the right to
     * see. Once the game is over the whole layout goes, because at that point
     * there is nothing left to protect and seeing what you missed is the point.
     */
    private static void send(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        if (game == null) {
            PacketDistributor.sendToPlayer(player,
                    MemorySyncPayload.menu(sizeOf(player).ordinal()));
            return;
        }
        UUID me = player.getUUID();
        boolean solo = game.solo();
        int tiles = game.board.size();

        List<String> texts = new ArrayList<>(MemorySyncPayload.TEXT_HEADER + tiles);
        texts.add(name(player));
        String them = "";
        if (!solo) {
            ServerPlayer other = online(player.getServer(), game.other(me));
            them = other == null ? "?" : name(other);
        }
        texts.add(them);

        List<Integer> nums = new ArrayList<>(MemorySyncPayload.HEADER + tiles);
        for (int i = 0; i < MemorySyncPayload.HEADER; i++) {
            nums.add(0);
        }
        int result = MemorySyncPayload.RESULT_NONE;
        if (game.done) {
            if (solo) {
                result = MemorySyncPayload.RESULT_WON;
            } else {
                int mine = game.scoreOf(me);
                int yours = game.scoreOf(game.other(me));
                if (game.forfeited != null) {
                    result = game.forfeited.equals(me)
                            ? MemorySyncPayload.RESULT_LOST : MemorySyncPayload.RESULT_WON;
                } else {
                    result = mine > yours ? MemorySyncPayload.RESULT_WON
                            : mine < yours ? MemorySyncPayload.RESULT_LOST
                            : MemorySyncPayload.RESULT_DRAW;
                }
            }
        }
        nums.set(MemorySyncPayload.PHASE,
                game.done ? MemorySyncPayload.PHASE_OVER : MemorySyncPayload.PHASE_PLAYING);
        nums.set(MemorySyncPayload.RESULT, result);
        nums.set(MemorySyncPayload.BOARD, game.size.ordinal());
        nums.set(MemorySyncPayload.COLS, game.size.cols);
        nums.set(MemorySyncPayload.ROWS, game.size.rows);
        nums.set(MemorySyncPayload.TILES, tiles);
        nums.set(MemorySyncPayload.MOVES, game.board.moves());
        nums.set(MemorySyncPayload.SCORE_YOU, solo ? game.board.matchedPairs() : game.scoreOf(me));
        nums.set(MemorySyncPayload.SCORE_THEM, solo ? 0 : game.scoreOf(game.other(me)));
        nums.set(MemorySyncPayload.YOUR_TURN, solo || me.equals(game.turn) ? 1 : 0);
        nums.set(MemorySyncPayload.SOLO, solo ? 1 : 0);
        nums.set(MemorySyncPayload.ELAPSED_S, (int) (((game.done ? game.doneMs
                : System.currentTimeMillis()) - game.startedMs) / 1000L));
        nums.set(MemorySyncPayload.PEEK_MS, game.peekUntilMs == 0 ? 0
                : (int) Math.max(0, game.peekUntilMs - System.currentTimeMillis()));

        List<String> reveal = game.done ? game.board.revealAll() : null;
        for (int i = 0; i < tiles; i++) {
            nums.add(game.board.stateAt(i));
            texts.add(reveal != null ? reveal.get(i) : game.board.faceAt(i));
        }
        PacketDistributor.sendToPlayer(player, new MemorySyncPayload(texts, nums));
    }

    private static void soundBoth(ServerPlayer player, Game game, SoundEvent event, float pitch) {
        sound(player, event, pitch);
        if (!game.solo()) {
            ServerPlayer them = online(player.getServer(), game.other(player.getUUID()));
            if (them != null) {
                sound(them, event, pitch);
            }
        }
    }

    private static void sound(ServerPlayer player, SoundEvent event, float pitch) {
        player.playNotifySound(event, SoundSource.PLAYERS, 0.7F, pitch);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
