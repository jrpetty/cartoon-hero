package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Bluff;
import com.jrpetty.mobtrumps.game.BluffAI;
import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs Bluff tables. One table per player, against computer seats.
 *
 * <p><b>The server holds every hand.</b> The client is sent its own cards and
 * nothing else — not the other seats' hands, not the face-down pile. That is
 * not tidiness, it is the game: a client that knows what is in the pile can see
 * through every bluff at the table, and a mod that ships that has no game left.
 * The only cards that ever cross the wire face up are the ones a finished
 * challenge has already turned over.
 *
 * <p>Computer seats move here too, so their reads are made against the real
 * hands and their bluffs are real bluffs rather than a client-side performance.
 */
public final class BluffManager {

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_YOUR_TURN = 1;
    public static final int PHASE_THEIR_TURN = 2;
    public static final int PHASE_WON = 3;
    public static final int PHASE_LOST = 4;

    /** What you can put up per game. */
    public static final int[] STAKES = {2, 4, 8, 16, 32, 64};
    public static final int DEFAULT_STAKE = 1;

    /** Names for the computer seats, in the order they are dealt. */
    private static final String[] AI_NAMES = {"Gus", "Marlowe", "Tilda"};

    /** One player's table. */
    private static final class Table {
        final Bluff game;
        final BluffAI[] brains;
        final Random random;
        final int seats;
        final int wagered;
        /** Seat the human sits in. Always 0 — but named, so nothing assumes it. */
        final int mySeat = 0;

        Table(int seats, int wagered) {
            this.seats = seats;
            this.wagered = wagered;
            this.random = new Random(ThreadLocalRandom.current().nextLong());
            this.game = new Bluff(seats, random);
            this.brains = new BluffAI[seats];
            for (int s = 0; s < seats; s++) {
                brains[s] = new BluffAI(game);
            }
        }

        void sawPlay(int seat, int cards) {
            for (BluffAI brain : brains) {
                brain.sawPlay(game, seat, cards);
            }
        }

        void roundTurnedOver() {
            for (BluffAI brain : brains) {
                brain.newRound(game);
            }
        }
    }

    private static final Map<UUID, Table> TABLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SEAT_CHOICE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> STAKE_CHOICE = new ConcurrentHashMap<>();

    private BluffManager() {
    }

    public static void open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BluffMenuPayload());
        send(player);
    }

    public static boolean isPlaying(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        return table != null && !table.game.done();
    }

    public static int seatsOf(ServerPlayer player) {
        return Math.max(Bluff.MIN_SEATS, Math.min(Bluff.MAX_SEATS,
                SEAT_CHOICE.getOrDefault(player.getUUID(), 4)));
    }

    public static int stakeIndexOf(ServerPlayer player) {
        int index = STAKE_CHOICE.getOrDefault(player.getUUID(), DEFAULT_STAKE);
        return Math.max(0, Math.min(STAKES.length - 1, index));
    }

    public static int stakeOf(ServerPlayer player) {
        return STAKES[stakeIndexOf(player)];
    }

    public static void handle(ServerPlayer player, int action, List<Integer> picks, int value) {
        switch (action) {
            case BluffActionPayload.NEW_GAME -> newGame(player);
            case BluffActionPayload.PLAY -> play(player, picks);
            case BluffActionPayload.CHALLENGE -> challenge(player);
            case BluffActionPayload.PASS -> pass(player);
            case BluffActionPayload.SET_SEATS -> setSeats(player, value);
            case BluffActionPayload.SET_STAKE -> setStake(player, value);
            case BluffActionPayload.LEAVE -> leave(player);
            case BluffActionPayload.FORFEIT -> forfeit(player);
            default -> {
                // an action this build does not know; ignore rather than trust it
            }
        }
    }

    /**
     * Close the screen without ending the game.
     *
     * <p>The stake is taken when the cards are dealt, so removing the table
     * here — which is what this used to do — meant pressing Escape burned the
     * wager and left nothing behind, silently. An unfinished hand is kept
     * instead and picked up where it was left the next time the player sits
     * down. Only a finished one is cleared away.
     */
    private static void leave(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        if (table != null && table.game.done()) {
            TABLES.remove(player.getUUID());
        } else if (table != null) {
            player.sendSystemMessage(Component.literal(
                            "Your hand is still on the table — sit back down to finish it.")
                    .withStyle(ChatFormatting.GRAY));
        }
        send(player);
    }

    /** Walk away and eat the wager, so nobody is ever stuck in a hand. */
    private static void forfeit(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        if (table == null) {
            return;
        }
        if (!table.game.done()) {
            StatsTracker.bump(player, "bluff_losses");
            player.sendSystemMessage(Component.literal("You folded. "
                            + table.wagered + " fragments lost.")
                    .withStyle(ChatFormatting.RED));
        }
        TABLES.remove(player.getUUID());
        send(player);
    }

    /** Changing the table only takes effect on the next deal. */
    private static void setSeats(ServerPlayer player, int seats) {
        if (isPlaying(player) || seats < Bluff.MIN_SEATS || seats > Bluff.MAX_SEATS) {
            return;
        }
        SEAT_CHOICE.put(player.getUUID(), seats);
        send(player);
    }

    private static void setStake(ServerPlayer player, int index) {
        if (isPlaying(player) || index < 0 || index >= STAKES.length) {
            return;
        }
        STAKE_CHOICE.put(player.getUUID(), index);
        send(player);
    }

    private static void newGame(ServerPlayer player) {
        if (isPlaying(player) || !BlockReach.canReach(player)) {
            return; // finish the hand you are in — or you have walked off
        }
        int stake = stakeOf(player);
        if (!RecyclerManager.takeFragments(player, stake)) {
            player.sendSystemMessage(Component.literal(
                            "You need " + stake + " fragments to sit down at that stake.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Table table = new Table(seatsOf(player), stake);
        TABLES.put(player.getUUID(), table);
        sound(player, SoundEvents.BOOK_PAGE_TURN, 1.3F);
        // whoever leads, let the computer seats act until it is the player's move
        runComputerSeats(player, table);
        finishIfOver(player, table);
        send(player);
    }

    private static void play(ServerPlayer player, List<Integer> picks) {
        Table table = TABLES.get(player.getUUID());
        if (table == null || table.game.done() || table.game.turn() != table.mySeat
                || !BlockReach.canReach(player)) {
            return;
        }
        if (picks == null || picks.isEmpty() || picks.size() > Bluff.MAX_PLAY) {
            return;
        }
        int before = table.game.handSize(table.mySeat);
        if (!table.game.play(table.mySeat, picks)) {
            return; // the engine refused it — a bad index, a repeat, or out of turn
        }
        table.sawPlay(table.mySeat, before - table.game.handSize(table.mySeat));
        sound(player, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        runComputerSeats(player, table);
        finishIfOver(player, table);
        send(player);
    }

    private static void challenge(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        if (table == null || table.game.done() || table.game.turn() != table.mySeat
                || !BlockReach.canReach(player)) {
            return;
        }
        if (!table.game.challenge(table.mySeat)) {
            return;
        }
        Bluff.Reveal reveal = table.game.lastReveal();
        boolean good = reveal != null && reveal.wasLying();
        sound(player, good ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_BREAK,
                good ? 1.2F : 0.8F);
        table.roundTurnedOver();
        runComputerSeats(player, table);
        finishIfOver(player, table);
        send(player);
    }

    private static void pass(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        if (table == null || table.game.done() || table.game.turn() != table.mySeat
                || !BlockReach.canReach(player)) {
            return;
        }
        if (!table.game.passOnExit(table.mySeat)) {
            return;
        }
        finishIfOver(player, table);
        send(player);
    }

    /**
     * Let every computer seat move until it is the player's turn again, or the
     * game ends. Bounded by the engine's own move cap so a policy bug cannot
     * spin the server thread.
     */
    private static void runComputerSeats(ServerPlayer player, Table table) {
        int guard = 0;
        while (!table.game.done() && table.game.turn() != table.mySeat
                && guard++ <= Bluff.MAX_MOVES) {
            int seat = table.game.turn();
            for (BluffAI brain : table.brains) {
                brain.sync(table.game);
            }
            boolean challenging = table.brains[seat].shouldChallenge(table.game, seat);
            if (challenging) {
                table.game.challenge(seat);
                table.roundTurnedOver();
                continue;
            }
            if (table.game.pendingOut() >= 0) {
                table.game.passOnExit(seat);
                continue;
            }
            List<Integer> picks = table.brains[seat].choosePlay(table.game, seat, table.random);
            int count = picks.size();
            if (!table.game.play(seat, picks)) {
                break; // should not happen; stop rather than spin
            }
            table.sawPlay(seat, count);
        }
    }

    /** Pay out once, the moment a table finishes. */
    private static void finishIfOver(ServerPlayer player, Table table) {
        if (!table.game.done()) {
            return;
        }
        boolean won = table.game.winner() == table.mySeat;
        StatsTracker.bump(player, won ? "bluff_wins" : "bluff_losses");
        if (won) {
            // the stake back, plus the same again for each opponent beaten
            int payout = table.wagered * table.seats;
            RecyclerManager.giveFragments(player, payout);
            player.sendSystemMessage(Component.literal("You emptied your hand — "
                            + payout + " fragments.").withStyle(ChatFormatting.GREEN));
            sound(player, SoundEvents.PLAYER_LEVELUP, 1.0F);
        } else {
            String name = seatName(table, table.game.winner());
            player.sendSystemMessage(Component.literal(name + " went out first. You lose "
                    + table.wagered + " fragments.").withStyle(ChatFormatting.RED));
        }
    }

    private static String seatName(Table table, int seat) {
        if (seat == table.mySeat) {
            return "You";
        }
        int index = seat - 1;
        return index >= 0 && index < AI_NAMES.length ? AI_NAMES[index] : "Seat " + seat;
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event,
                              float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundSource.PLAYERS, 0.7F, pitch);
    }

    /** Push the table to its player. */
    public static void send(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        List<String> hand = new ArrayList<>();
        List<Integer> nums = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> log = new ArrayList<>();
        List<String> reveal = new ArrayList<>();

        if (table == null) {
            int phase = PHASE_IDLE;
            for (int i = 0; i < BluffSyncPayload.NUM_HAND_SIZES; i++) {
                nums.add(0);
            }
            nums.set(BluffSyncPayload.NUM_SEATS, seatsOf(player));
            nums.set(BluffSyncPayload.NUM_STAKE, stakeIndexOf(player));
            nums.set(BluffSyncPayload.NUM_WINNER, -1);
            nums.set(BluffSyncPayload.NUM_LAST_SEAT, -1);
            nums.set(BluffSyncPayload.NUM_PENDING_OUT, -1);
            PacketDistributor.sendToPlayer(player,
                    new BluffSyncPayload(phase, hand, nums, names, log, reveal));
            return;
        }

        Bluff game = table.game;
        for (MobCard card : game.hand(table.mySeat)) {
            hand.add(card.id());
        }
        for (int i = 0; i < BluffSyncPayload.NUM_HAND_SIZES + table.seats; i++) {
            nums.add(0);
        }
        nums.set(BluffSyncPayload.NUM_MY_SEAT, table.mySeat);
        nums.set(BluffSyncPayload.NUM_SEATS, table.seats);
        nums.set(BluffSyncPayload.NUM_TURN, game.turn());
        nums.set(BluffSyncPayload.NUM_PILE, game.pileSize());
        nums.set(BluffSyncPayload.NUM_LAST_SEAT, game.lastSeat());
        nums.set(BluffSyncPayload.NUM_LAST_COUNT, game.lastCount());
        nums.set(BluffSyncPayload.NUM_PENDING_OUT, game.pendingOut());
        nums.set(BluffSyncPayload.NUM_WINNER, game.winner());
        nums.set(BluffSyncPayload.NUM_CLAIM_A, game.claim().a());
        nums.set(BluffSyncPayload.NUM_CLAIM_VALUE, game.claim().value());
        nums.set(BluffSyncPayload.NUM_CLAIM_B, game.claim().b());
        nums.set(BluffSyncPayload.NUM_BURNT, game.burntCount());
        nums.set(BluffSyncPayload.NUM_STAKE, stakeIndexOf(player));
        nums.set(BluffSyncPayload.NUM_MOVES, game.moves());
        Bluff.Reveal last = game.lastReveal();
        if (last != null) {
            nums.set(BluffSyncPayload.NUM_REVEAL_CHALLENGER, last.challenger());
            nums.set(BluffSyncPayload.NUM_REVEAL_ACCUSED, last.accused());
            nums.set(BluffSyncPayload.NUM_REVEAL_WAS_LYING, last.wasLying() ? 1 : 0);
            nums.set(BluffSyncPayload.NUM_REVEAL_TAKEN, last.pileTaken());
            for (MobCard card : last.cards()) {
                reveal.add(card.id());
            }
        } else {
            nums.set(BluffSyncPayload.NUM_REVEAL_CHALLENGER, -1);
            nums.set(BluffSyncPayload.NUM_REVEAL_ACCUSED, -1);
        }
        for (int s = 0; s < table.seats; s++) {
            nums.set(BluffSyncPayload.NUM_HAND_SIZES + s, game.handSize(s));
            names.add(seatName(table, s));
        }
        // the tail of the log; the whole thing would grow without bound
        List<String> full = game.log();
        log.addAll(full.subList(Math.max(0, full.size() - 40), full.size()));

        int phase;
        if (game.done()) {
            phase = game.winner() == table.mySeat ? PHASE_WON : PHASE_LOST;
        } else {
            phase = game.turn() == table.mySeat ? PHASE_YOUR_TURN : PHASE_THEIR_TURN;
        }
        PacketDistributor.sendToPlayer(player,
                new BluffSyncPayload(phase, hand, nums, names, log, reveal));
    }

    /**
     * Whether the player has an unfinished hand waiting. The table menu uses
     * this to say "resume" rather than "deal", so a kept game is visible rather
     * than a surprise.
     */
    public static boolean hasUnfinished(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        return table != null && !table.game.done();
    }
}
