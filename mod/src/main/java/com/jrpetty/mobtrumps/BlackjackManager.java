package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Blackjack;
import com.jrpetty.mobtrumps.game.Stat;
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
 * The Twenty-One table: one hand per player, against the house.
 *
 * <p>The whole hand lives on the server. The client sends "call Speed" and
 * nothing else — it never learns the shoe, never turns a card, and cannot ask
 * for a stat it has already spent. That matters more here than in a duel,
 * because there is a wager on the table and a client that could peek at the
 * next card would simply never lose.
 *
 * <p>Built as one player against a dealer, which is the game as asked for. It is
 * shaped so a shared dealer can seat several players later without the hand
 * logic changing: a {@link Blackjack} is already self-contained per player, and
 * a multiplayer table is that plus one dealer hand settled against each.
 */
public final class BlackjackManager {

    private static final Map<UUID, Blackjack> TABLES = new ConcurrentHashMap<>();
    /** The stake each player last chose, remembered between hands. */
    private static final Map<UUID, Integer> STAKE_CHOICE = new ConcurrentHashMap<>();
    /** What the hand in progress was actually staked at, so a later change cannot
     *  alter what an already-dealt hand pays out. */
    private static final Map<UUID, Integer> WAGERED = new ConcurrentHashMap<>();

    private BlackjackManager() {
    }

    public static boolean isPlaying(ServerPlayer player) {
        Blackjack game = TABLES.get(player.getUUID());
        return game != null && !game.isFinished();
    }

    /** Open the screen, without dealing — the player pays when they deal. */
    public static void open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BlackjackMenuPayload());
        send(player);
    }

    public static void handle(ServerPlayer player, int action, int statOrdinal) {
        switch (action) {
            case BlackjackActionPayload.DEAL -> deal(player);
            case BlackjackActionPayload.CALL -> call(player, statOrdinal);
            case BlackjackActionPayload.STAND -> stand(player);
            case BlackjackActionPayload.LEAVE -> TABLES.remove(player.getUUID());
            case BlackjackActionPayload.SET_STAKE -> setStake(player, statOrdinal);
            default -> {
            }
        }
    }

    /** The stake a player is currently betting, in fragments. */
    public static int stakeOf(ServerPlayer player) {
        return Blackjack.STAKES[stakeIndex(player)];
    }

    private static int stakeIndex(ServerPlayer player) {
        return Blackjack.clampStakeIndex(
                STAKE_CHOICE.getOrDefault(player.getUUID(), Blackjack.DEFAULT_STAKE));
    }

    /**
     * Change the wager. Refused mid-hand: the stake is taken when the hand is
     * dealt, so allowing a change afterwards would let a player raise a winning
     * hand and shrink a losing one.
     */
    private static void setStake(ServerPlayer player, int index) {
        if (isPlaying(player) || index < 0 || index >= Blackjack.STAKES.length) {
            return;
        }
        STAKE_CHOICE.put(player.getUUID(), index);
        send(player);
    }

    private static void deal(ServerPlayer player) {
        if (!BlockReach.canReach(player)) {
            return;
        }
        if (isPlaying(player)) {
            return; // already in a hand; finish it first
        }
        int stake = stakeOf(player);
        if (!RecyclerManager.takeFragments(player, stake)) {
            player.sendSystemMessage(Component.literal(
                            "You need " + stake + " fragments to bet that much.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // remember what was actually staked, so the payout cannot drift if the
        // player changes their bet before the next deal
        WAGERED.put(player.getUUID(), stake);
        TABLES.put(player.getUUID(), new Blackjack(ThreadLocalRandom.current()));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.4F);
        send(player);
    }

    private static void call(ServerPlayer player, int statOrdinal) {
        Blackjack game = TABLES.get(player.getUUID());
        if (game == null || game.isFinished() || !BlockReach.canReach(player)) {
            return;
        }
        Stat[] stats = Stat.values();
        if (statOrdinal < 0 || statOrdinal >= stats.length) {
            return;
        }
        Stat stat = stats[statOrdinal];
        if (!game.canHit()) {
            return; // hand has run its full length — refuse rather than trust the client
        }
        Blackjack.Draw draw = game.hit(stat);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                draw.total() > Blackjack.TARGET ? SoundEvents.ITEM_BREAK
                        : SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.7F, draw.total() > Blackjack.TARGET ? 0.8F : 1.1F);
        if (game.isFinished()) {
            payOut(player, game);
        }
        send(player);
    }

    private static void stand(ServerPlayer player) {
        Blackjack game = TABLES.get(player.getUUID());
        if (game == null || game.isFinished() || !BlockReach.canReach(player)) {
            return;
        }
        game.stand();
        payOut(player, game);
        send(player);
    }

    /**
     * Settle the wager. A win returns the stake and matches it, a push returns
     * it, and a loss keeps it — the plain table payout, with no bonus for
     * landing exactly on {@value Blackjack#TARGET}. A bonus there was measured
     * to hand the player an edge, and a table nobody can lose at is not a table.
     */
    private static void payOut(ServerPlayer player, Blackjack game) {
        int stake = WAGERED.getOrDefault(player.getUUID(), stakeOf(player));
        switch (game.result()) {
            case PLAYER_WIN -> {
                RecyclerManager.giveFragments(player, stake * 2);
                StatsTracker.bump(player, "twentyone_wins");
                player.sendSystemMessage(Component.literal(
                                "Twenty-One: " + game.playerTotal() + " beats "
                                        + describeDealer(game) + " — you win " + stake + ".")
                        .withStyle(ChatFormatting.GREEN));
            }
            case PUSH -> {
                RecyclerManager.giveFragments(player, stake);
                player.sendSystemMessage(Component.literal(
                                "Twenty-One: both on " + game.playerTotal() + " — stake returned.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            default -> player.sendSystemMessage(Component.literal(
                            "Twenty-One: " + (game.playerBust()
                                    ? "bust on " + game.playerTotal()
                                    : game.playerTotal() + " loses to " + describeDealer(game))
                                    + " — " + stake + " fragments gone.")
                    .withStyle(ChatFormatting.RED));
        }
        WAGERED.remove(player.getUUID());
        StatsTracker.bump(player, "twentyone_played");
    }

    private static String describeDealer(Blackjack game) {
        return game.dealerBust() ? "a dealer bust" : String.valueOf(game.dealerTotal());
    }

    // --- wire format ---------------------------------------------------------

    private static List<String> encode(List<Blackjack.Draw> draws) {
        List<String> out = new ArrayList<>(draws.size());
        for (Blackjack.Draw d : draws) {
            out.add(d.stat().ordinal() + "|" + d.cardId() + "|" + d.value() + "|" + d.total());
        }
        return out;
    }

    private static void send(ServerPlayer player) {
        Blackjack game = TABLES.get(player.getUUID());
        if (game == null) {
            PacketDistributor.sendToPlayer(player, new BlackjackSyncPayload(
                    BlackjackSyncPayload.PHASE_DONE, BlackjackSyncPayload.RESULT_NONE,
                    List.of(0, 0, 0, RecyclerManager.fragments(player),
                            stakeOf(player), stakeIndex(player)),
                    List.of(), List.of()));
            return;
        }
        int phase = switch (game.phase()) {
            case PLAYER -> BlackjackSyncPayload.PHASE_PLAYER;
            case DEALER -> BlackjackSyncPayload.PHASE_DEALER;
            case DONE -> BlackjackSyncPayload.PHASE_DONE;
        };
        int result = switch (game.result()) {
            case PLAYER_WIN -> BlackjackSyncPayload.RESULT_PLAYER;
            case DEALER_WIN -> BlackjackSyncPayload.RESULT_DEALER;
            case PUSH -> BlackjackSyncPayload.RESULT_PUSH;
            case NONE -> BlackjackSyncPayload.RESULT_NONE;
        };
        // the dealer's hand is only revealed once it is actually playing, so a
        // packet sniffer learns nothing the table has not already turned over
        List<String> dealer = game.phase() == Blackjack.Phase.PLAYER
                ? List.of() : encode(game.dealerDraws());
        PacketDistributor.sendToPlayer(player, new BlackjackSyncPayload(
                phase, result,
                List.of(game.playerTotal(), game.dealerTotal(), game.drawsTaken(),
                        RecyclerManager.fragments(player),
                        WAGERED.getOrDefault(player.getUUID(), stakeOf(player)),
                        stakeIndex(player)),
                encode(game.playerDraws()), dealer));
    }

    /** Drop a player's table when they log out; an unfinished hand is forfeit. */
    public static void handleLogout(ServerPlayer player) {
        TABLES.remove(player.getUUID());
        WAGERED.remove(player.getUUID());
    }

}
