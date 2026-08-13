package com.voxelia.mmo.game;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.network.MemoryStatePayload;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side registry for {@link MemoryGame}: who is playing what, pending
 * invites, the mismatch-peek countdown, and pushing state to every seat.
 * Games live in memory only — a restart clears the tables, which is what you
 * want from a card game.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class MemoryGames {
    private MemoryGames() {}

    /** An invite waits this many ticks (60s) before it lapses. */
    private static final int INVITE_TICKS = 20 * 60;

    private record Invite(UUID from, String fromName, MemoryGame.Difficulty difficulty, int ticksLeft) {}

    private static final List<MemoryGame> GAMES = new ArrayList<>();
    private static final Map<UUID, MemoryGame> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static long seedCounter = 0L;

    public static MemoryGame gameOf(ServerPlayer player) {
        return BY_PLAYER.get(player.getUUID());
    }

    /** Starts (or restarts) a solo game for one player. */
    public static void startSolo(ServerPlayer player, MemoryGame.Difficulty difficulty) {
        leave(player, false);
        MemoryGame game = newGame(difficulty);
        game.addPlayer(player.getUUID(), player.getGameProfile().getName());
        register(game);
        sync(player.getServer(), game);
    }

    /** Offers a versus game; the target accepts with /voxelia memory accept. */
    public static void invite(ServerPlayer from, ServerPlayer target, MemoryGame.Difficulty difficulty) {
        INVITES.put(target.getUUID(),
            new Invite(from.getUUID(), from.getGameProfile().getName(), difficulty, INVITE_TICKS));

        target.sendSystemMessage(Component.literal("")
            .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(from.getGameProfile().getName()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" challenged you to Memory (" + difficulty.display()
                + "). Type ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("/voxelia memory accept").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" within 60s.").withStyle(ChatFormatting.GRAY)));

        from.sendSystemMessage(Component.literal("")
            .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("Challenge sent to " + target.getGameProfile().getName() + ".")
                .withStyle(ChatFormatting.GRAY)));
    }

    /** Accepts the pending invite. Returns null on success, or a reason to show the player. */
    public static Component accept(ServerPlayer player) {
        Invite invite = INVITES.remove(player.getUUID());
        if (invite == null) return Component.literal("You have no pending Memory challenge.");

        MinecraftServer server = player.getServer();
        ServerPlayer host = server == null ? null : server.getPlayerList().getPlayer(invite.from());
        if (host == null) return Component.literal("That player is no longer online.");

        leave(player, false);
        leave(host, false);
        MemoryGame game = newGame(invite.difficulty());
        game.addPlayer(host.getUUID(), host.getGameProfile().getName());
        game.addPlayer(player.getUUID(), player.getGameProfile().getName());
        register(game);

        Component line = Component.literal("")
            .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("Memory: " + host.getGameProfile().getName() + " vs "
                + player.getGameProfile().getName() + " (" + invite.difficulty().display() + "). "
                + host.getGameProfile().getName() + " goes first.").withStyle(ChatFormatting.YELLOW));
        host.sendSystemMessage(line);
        player.sendSystemMessage(line);
        sync(server, game);
        return null;
    }

    /** A flip request from the client. */
    public static void flip(ServerPlayer player, int index) {
        MemoryGame game = gameOf(player);
        if (game == null) return;
        int before = game.pairsLeft();
        if (!game.flip(player.getUUID(), index)) return;

        MinecraftServer server = player.getServer();
        sync(server, game); // clients play the flip/match/miss cues off the state change
        if (game.finished()) announceResult(server, game);
    }

    /** Quits a game; {@code tellOthers} announces the forfeit to the opponent. */
    public static void leave(ServerPlayer player, boolean tellOthers) {
        MemoryGame game = BY_PLAYER.remove(player.getUUID());
        if (game == null) return;
        MinecraftServer server = player.getServer();
        game.forfeit(player.getUUID());

        if (tellOthers) {
            for (UUID other : game.players()) {
                ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayer(other);
                if (sp != null) {
                    sp.sendSystemMessage(Component.literal("")
                        .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(player.getGameProfile().getName() + " left the game — you win.")
                            .withStyle(ChatFormatting.YELLOW)));
                }
            }
        }
        if (game.players().isEmpty()) {
            GAMES.remove(game);
        } else {
            sync(server, game);
        }
        // The quitter's screen needs an empty board so it falls back to the lobby.
        PacketDistributor.sendToPlayer(player, MemoryStatePayload.empty());
    }

    /** Pushes the current board to every seat (each gets only the faces it may see). */
    public static void sync(MinecraftServer server, MemoryGame game) {
        if (server == null) return;
        boolean opening = game.consumeJustStarted();
        for (UUID id : game.players()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                PacketDistributor.sendToPlayer(sp, MemoryStatePayload.of(game, id, opening));
            }
        }
    }

    /** Re-sends the board to one player (screen opened, or /voxelia memory with a game running). */
    public static void syncTo(ServerPlayer player) {
        MemoryGame game = gameOf(player);
        if (game == null) {
            PacketDistributor.sendToPlayer(player, MemoryStatePayload.empty());
        } else {
            PacketDistributor.sendToPlayer(player, MemoryStatePayload.of(game, player.getUUID(), true));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!INVITES.isEmpty()) {
            INVITES.entrySet().removeIf(e -> e.getValue().ticksLeft() <= 1);
            INVITES.replaceAll((k, v) -> new Invite(v.from(), v.fromName(), v.difficulty(), v.ticksLeft() - 1));
        }
        if (GAMES.isEmpty()) return;
        for (MemoryGame game : new ArrayList<>(GAMES)) {
            if (game.tick()) sync(event.getServer(), game);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INVITES.remove(player.getUUID());
            if (BY_PLAYER.containsKey(player.getUUID())) leave(player, true);
        }
    }

    private static MemoryGame newGame(MemoryGame.Difficulty difficulty) {
        long seed = System.nanoTime() ^ (seedCounter++ * 0x9E3779B97F4A7C15L);
        return new MemoryGame(difficulty, Skill.values().length, seed);
    }

    private static void register(MemoryGame game) {
        GAMES.add(game);
        for (UUID id : game.players()) BY_PLAYER.put(id, game);
    }

    private static void announceResult(MinecraftServer server, MemoryGame game) {
        if (server == null) return;
        Component result;
        if (game.versus()) {
            int w = game.winner();
            StringBuilder score = new StringBuilder();
            for (int i = 0; i < game.scores().size(); i++) {
                if (i > 0) score.append(" – ");
                score.append(game.scores().get(i));
            }
            result = w < 0
                ? Component.literal("Memory: a draw at " + score + "!").withStyle(ChatFormatting.YELLOW)
                : Component.literal("Memory: " + game.names().get(w) + " wins " + score + "!")
                    .withStyle(ChatFormatting.GOLD);
        } else {
            result = Component.literal("Memory solved in " + game.moves() + " moves, "
                + MemoryGame.formatTime(game.elapsedSeconds()) + "!").withStyle(ChatFormatting.GOLD);
        }
        for (UUID id : game.players()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                sp.sendSystemMessage(Component.literal("")
                    .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
                    .append(result));
            }
        }
    }
}
