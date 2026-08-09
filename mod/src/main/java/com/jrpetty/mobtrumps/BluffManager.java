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
        /**
         * Who is in each seat, by index. A null entry is a computer player.
         *
         * <p>Solo is simply the case where only seat zero is filled, which is
         * why nothing below asks whether the table is "multiplayer" — it asks
         * whose seat it is and whether anybody is sitting in it.
         */
        final UUID[] occupants;
        /** Last known display name per seat; null for a computer seat. */
        final String[] names;
        /** Settled once, however many people are sitting down. */
        boolean settled;

        Table(int seats, int wagered, UUID[] occupants) {
            this.seats = seats;
            this.wagered = wagered;
            this.occupants = occupants;
            this.names = new String[seats];
            this.random = new Random(ThreadLocalRandom.current().nextLong());
            this.game = new Bluff(seats, random);
            this.brains = new BluffAI[seats];
            for (int s = 0; s < seats; s++) {
                brains[s] = new BluffAI(game);
            }
        }

        /** Ticks until the next computer move. */
        int aiWait;

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

    /** Every human at a table maps to the SAME Table instance. */
    private static final Map<UUID, Table> TABLES = new ConcurrentHashMap<>();
    /** Pending challenges: invitee -> {challenger, expiry, seats}. */
    private static final Map<UUID, Object[]> INVITES = new ConcurrentHashMap<>();
    /** How long an unanswered challenge stands. */
    private static final long INVITE_MS = 60_000L;

    /** Which seat this player occupies at their table, or -1. */
    private static int seatOf(Table table, UUID who) {
        for (int i = 0; i < table.occupants.length; i++) {
            if (who.equals(table.occupants[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Push the table to everyone sitting at it. */
    private static void sendAll(net.minecraft.server.MinecraftServer server, Table table) {
        for (UUID id : table.occupants) {
            if (id == null) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                send(p);
            }
        }
    }
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
            // a shared table is only forgotten once, for everyone on it
            for (UUID id : table.occupants) {
                if (id != null) {
                    TABLES.remove(id);
                }
            }
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
            // Folding out of a hand with other people in it hands them the pot
            // rather than quietly deleting a table they were still playing.
            int seat = seatOf(table, player.getUUID());
            if (seat >= 0) {
                table.occupants[seat] = null;   // the seat plays itself from here
                table.names[seat] = null;
            }
            for (UUID id : table.occupants) {
                if (id != null && player.getServer() != null) {
                    ServerPlayer p = player.getServer().getPlayerList().getPlayer(id);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal(
                                        player.getGameProfile().getName() + " folded.")
                                .withStyle(ChatFormatting.GRAY));
                        send(p);
                    }
                }
            }
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
        int seats = seatsOf(player);
        UUID[] occupants = new UUID[seats];
        occupants[0] = player.getUUID();   // the rest are computer seats
        Table table = new Table(seats, stake, occupants);
        TABLES.put(player.getUUID(), table);
        sound(player, ModSounds.SHUFFLE.get(), 1.0F);   // the table being dealt
        // whoever leads, the computer seats now move on the server clock, one
        // beat at a time, so the player watches the round open
        armAi(table, FIRST_MOVE_TICKS);
        send(player);
    }

    private static void play(ServerPlayer player, List<Integer> picks) {
        Table table = TABLES.get(player.getUUID());
        if (table == null || table.game.done() || !BlockReach.canReach(player)) {
            return;
        }
        int seat = seatOf(table, player.getUUID());
        if (seat < 0 || table.game.turn() != seat) {
            return;
        }
        if (picks == null || picks.isEmpty() || picks.size() > Bluff.MAX_PLAY) {
            return;
        }
        int before = table.game.handSize(seat);
        if (!table.game.play(seat, picks)) {
            return; // the engine refused it — a bad index, a repeat, or out of turn
        }
        table.sawPlay(seat, before - table.game.handSize(seat));
        sound(player, ModSounds.CARD_PLACE.get(), 1.0F);   // face down on the felt
        armAi(table, FIRST_MOVE_TICKS);
        finishIfOver(player.getServer(), table);
        sendAll(player.getServer(), table);
    }

    private static void challenge(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        if (table == null || table.game.done() || !BlockReach.canReach(player)) {
            return;
        }
        int seat = seatOf(table, player.getUUID());
        if (seat < 0 || table.game.turn() != seat || !table.game.challenge(seat)) {
            return;
        }
        Bluff.Reveal reveal = table.game.lastReveal();
        boolean good = reveal != null && reveal.wasLying();
        if (good) {
            // catches are worth counting: they are the skill in the game
            StatsTracker.bump(player, "bluff_catches");
            // and a catch that sheds your own last card ends the game on the
            // spot — the rarest way there is to win a hand
            if (table.game.done() && table.game.winner() == seat) {
                StatsTracker.bump(player, "bluff_lastcard");
            }
            ServerSync.markAwards(player);
        }
        sound(player, good ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_BREAK,
                good ? 1.2F : 0.8F);
        table.roundTurnedOver();
        // a longer beat after a reveal, so what was turned over can be read
        armAi(table, REVEAL_PAUSE_TICKS);
        finishIfOver(player.getServer(), table);
        sendAll(player.getServer(), table);
    }

    private static void pass(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        if (table == null || table.game.done() || !BlockReach.canReach(player)) {
            return;
        }
        int seat = seatOf(table, player.getUUID());
        if (seat < 0 || table.game.turn() != seat || !table.game.passOnExit(seat)) {
            return;
        }
        finishIfOver(player.getServer(), table);
        sendAll(player.getServer(), table);
    }

    /** Ticks before the first computer response to something the player did. */
    private static final int FIRST_MOVE_TICKS = 14;
    /** Ticks between one computer move and the next. */
    private static final int MOVE_TICKS = 16;
    /** Extra beat after a challenge, so the revealed cards can be read. */
    private static final int REVEAL_PAUSE_TICKS = 34;

    private static void armAi(Table table, int base) {
        table.aiWait = base + table.random.nextInt(9);
    }

    /**
     * Advance every table whose turn belongs to a computer seat — one move per
     * beat, so a round unfolds in front of the player instead of resolving
     * inside their own button press. Before this, all three opponents moved in
     * the same instant and the "is thinking" line was dead text: the player
     * only ever saw the aftermath of a round, never the round.
     *
     * <p>A table whose player is offline simply waits; nothing is paid out or
     * lost while they cannot see it.
     */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (TABLES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Table> entry : TABLES.entrySet()) {
            Table table = entry.getValue();
            Bluff game = table.game;
            if (game.done()) {
                continue;
            }
            // one entry per human, but the table is shared — only advance it from
            // the seat whose turn it actually is
            int turn = game.turn();
            UUID sitting = table.occupants[turn];
            if (sitting != null && server.getPlayerList().getPlayer(sitting) != null) {
                continue; // a present human owes this move
            }
            if (!entry.getKey().equals(firstHuman(table))) {
                continue; // drive each table once per tick, not once per player
            }
            if (--table.aiWait > 0) {
                continue;
            }
            // an empty seat, or one whose player has gone, plays itself — a
            // table must never deadlock on somebody who logged off mid-hand
            aiMoveOnce(server, table);
        }
    }

    /** The lowest-seated human, so a shared table is advanced exactly once. */
    private static UUID firstHuman(Table table) {
        for (UUID id : table.occupants) {
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** One unattended seat takes one move, and everyone at the table sees it. */
    private static void aiMoveOnce(net.minecraft.server.MinecraftServer server, Table table) {
        Bluff game = table.game;
        int seat = game.turn();
        for (BluffAI brain : table.brains) {
            brain.sync(game);
        }
        if (table.brains[seat].shouldChallenge(game, seat)) {
            game.challenge(seat);
            table.roundTurnedOver();
            Bluff.Reveal reveal = game.lastReveal();
            boolean caught = reveal != null && reveal.wasLying();
            // quieter than the player's own sounds — it is across the table
            soundAll(server, table, caught ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_BREAK,
                    caught ? 0.9F : 1.1F, 0.4F);
            armAi(table, REVEAL_PAUSE_TICKS);
        } else if (game.pendingOut() >= 0) {
            game.passOnExit(seat);
            armAi(table, MOVE_TICKS);
        } else {
            List<Integer> picks = table.brains[seat].choosePlay(game, seat, table.random);
            int count = picks.size();
            if (!game.play(seat, picks)) {
                // should be impossible; freeze this table rather than spin it
                table.aiWait = Integer.MAX_VALUE;
                return;
            }
            table.sawPlay(seat, count);
            soundAll(server, table, ModSounds.CARD_PLACE.get(), 0.9F + 0.06F * seat, 0.35F);
            armAi(table, MOVE_TICKS);
        }
        finishIfOver(server, table);
        sendAll(server, table);
    }

    private static void soundAll(net.minecraft.server.MinecraftServer server, Table table,
                                 net.minecraft.sounds.SoundEvent event, float pitch, float vol) {
        for (UUID id : table.occupants) {
            if (id == null) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                sound(p, event, pitch, vol);
            }
        }
    }

    /**
     * Settle the table, once, the moment it finishes.
     *
     * <p>Everybody sitting down paid the same wager in, so the winner takes the
     * lot. A seat played by the computer still contributes its share — the
     * house covers it — which is what keeps a mixed table paying the same as a
     * full one.
     */
    private static void finishIfOver(net.minecraft.server.MinecraftServer server, Table table) {
        if (!table.game.done() || table.settled) {
            return;
        }
        table.settled = true;
        int winner = table.game.winner();
        for (UUID id : table.occupants) {
            if (id == null) {
                continue;
            }
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            boolean won = seatOf(table, id) == winner;
            StatsTracker.bump(p, won ? "bluff_wins" : "bluff_losses");
            ServerSync.markAwards(p);
            if (won) {
                int payout = table.wagered * table.seats;
                RecyclerManager.giveFragments(p, payout);
                p.sendSystemMessage(Component.literal("You emptied your hand — "
                        + payout + " fragments.").withStyle(ChatFormatting.GREEN));
                sound(p, SoundEvents.PLAYER_LEVELUP, 1.0F);
            } else {
                p.sendSystemMessage(Component.literal(seatName(table, winner)
                        + " went out first. You lose " + table.wagered + " fragments.")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Display name of a seat, from the table's own point of view. */
    private static String seatName(Table table, int seat) {
        if (seat < 0 || seat >= table.seats) {
            return "Seat " + seat;
        }
        String held = table.names[seat];
        if (held != null) {
            return held;
        }
        int index = seat % AI_NAMES.length;
        return AI_NAMES[index];
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event,
                              float pitch) {
        sound(player, event, pitch, 0.7F);
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event,
                              float pitch, float volume) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundSource.PLAYERS, volume, pitch);
    }

    /** The numeric block for a player with no board in front of them. */
    private static List<Integer> idleNums(ServerPlayer player) {
        List<Integer> nums = new ArrayList<>();
        for (int i = 0; i < BluffSyncPayload.NUM_HAND_SIZES; i++) {
            nums.add(0);
        }
        nums.set(BluffSyncPayload.NUM_SEATS, seatsOf(player));
        nums.set(BluffSyncPayload.NUM_STAKE, stakeIndexOf(player));
        nums.set(BluffSyncPayload.NUM_WINNER, -1);
        nums.set(BluffSyncPayload.NUM_LAST_SEAT, -1);
        nums.set(BluffSyncPayload.NUM_PENDING_OUT, -1);
        return nums;
    }

    /** Push the table to its player. Each client only ever gets its own hand. */
    public static void send(ServerPlayer player) {
        Table table = TABLES.get(player.getUUID());
        List<String> hand = new ArrayList<>();
        List<Integer> nums = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> log = new ArrayList<>();
        List<String> reveal = new ArrayList<>();

        if (table == null) {
            PacketDistributor.sendToPlayer(player,
                    new BluffSyncPayload(PHASE_IDLE, List.of(), idleNums(player),
                            List.of(), List.of(), List.of()));
            return;
        }

        Bluff game = table.game;
        int mySeat = seatOf(table, player.getUUID());
        if (mySeat < 0) {
            // Not seated here. Falling back to seat zero would have posted
            // somebody else's hand to them, which is the one thing this whole
            // game rests on not happening.
            PacketDistributor.sendToPlayer(player,
                    new BluffSyncPayload(PHASE_IDLE, List.of(),
                            idleNums(player), List.of(), List.of(), List.of()));
            return;
        }
        for (MobCard card : game.hand(mySeat)) {
            hand.add(card.id());
        }
        for (int i = 0; i < BluffSyncPayload.NUM_HAND_SIZES + table.seats; i++) {
            nums.add(0);
        }
        nums.set(BluffSyncPayload.NUM_MY_SEAT, mySeat);
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
        table.names[mySeat] = player.getGameProfile().getName();
        for (int s = 0; s < table.seats; s++) {
            nums.set(BluffSyncPayload.NUM_HAND_SIZES + s, game.handSize(s));
            names.add(s == mySeat ? "You" : seatName(table, s));
        }
        // the tail of the log; the whole thing would grow without bound
        List<String> full = game.log();
        log.addAll(full.subList(Math.max(0, full.size() - 40), full.size()));

        int phase;
        if (game.done()) {
            phase = game.winner() == mySeat ? PHASE_WON : PHASE_LOST;
        } else {
            phase = game.turn() == mySeat ? PHASE_YOUR_TURN : PHASE_THEIR_TURN;
        }
        PacketDistributor.sendToPlayer(player,
                new BluffSyncPayload(phase, hand, nums, names, log, reveal));
    }

    // --- playing real people ------------------------------------------------

    /**
     * Challenge somebody to a hand. Both put the same wager up and the winner
     * takes the table. Seats nobody is sitting in are still played by the
     * house, so a two-player challenge at a four-seat table is a four-way game
     * and the pot is the same either way.
     */
    public static int challenge(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            from.sendSystemMessage(Component.literal("You cannot play yourself.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (isPlaying(from) || isPlaying(to)) {
            from.sendSystemMessage(Component.literal("One of you is already in a hand.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        INVITES.put(to.getUUID(), new Object[]{from.getUUID(),
                System.currentTimeMillis() + INVITE_MS, stakeOf(from)});
        to.sendSystemMessage(Component.literal(from.getGameProfile().getName()
                        + " wants a hand of Mob Bluff for " + stakeOf(from)
                        + " fragments  —  /mobtrumps bluff accept")
                .withStyle(ChatFormatting.GOLD));
        from.sendSystemMessage(Component.literal("Challenge sent to "
                + to.getGameProfile().getName() + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * Take a challenge on.
     *
     * <p>Both wagers are checked before either is taken. Taking one and then
     * finding the other player short would leave the first player's fragments
     * gone with no board to show for them.
     */
    public static int accept(ServerPlayer target) {
        Object[] invite = INVITES.remove(target.getUUID());
        if (invite == null || System.currentTimeMillis() > (Long) invite[1]) {
            target.sendSystemMessage(Component.literal("No challenge is waiting.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer from = target.getServer() == null ? null
                : target.getServer().getPlayerList().getPlayer((UUID) invite[0]);
        if (from == null) {
            target.sendSystemMessage(Component.literal("They have gone offline.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (isPlaying(from) || isPlaying(target)) {
            target.sendSystemMessage(Component.literal("One of you is already in a hand.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int stake = Math.max(STAKES[0], (Integer) invite[2]);
        for (ServerPlayer p : new ServerPlayer[]{from, target}) {
            if (RecyclerManager.fragments(p) < stake) {
                String msg = p.getGameProfile().getName() + " cannot cover "
                        + stake + " fragments.";
                from.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                target.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                return 0;
            }
        }
        if (!RecyclerManager.takeFragments(from, stake)) {
            return 0;
        }
        if (!RecyclerManager.takeFragments(target, stake)) {
            RecyclerManager.giveFragments(from, stake);  // put the first one back
            return 0;
        }

        int seats = Math.max(2, seatsOf(from));
        UUID[] occupants = new UUID[seats];
        occupants[0] = from.getUUID();
        occupants[1] = target.getUUID();
        Table table = new Table(seats, stake, occupants);
        table.names[0] = from.getGameProfile().getName();
        table.names[1] = target.getGameProfile().getName();
        TABLES.put(from.getUUID(), table);
        TABLES.put(target.getUUID(), table);
        armAi(table, FIRST_MOVE_TICKS);
        open(from);
        open(target);
        return 1;
    }

    public static int decline(ServerPlayer target) {
        Object[] invite = INVITES.remove(target.getUUID());
        if (invite == null) {
            target.sendSystemMessage(Component.literal("No challenge is waiting.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer from = target.getServer() == null ? null
                : target.getServer().getPlayerList().getPlayer((UUID) invite[0]);
        if (from != null) {
            from.sendSystemMessage(Component.literal(target.getGameProfile().getName()
                    + " declined.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
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
