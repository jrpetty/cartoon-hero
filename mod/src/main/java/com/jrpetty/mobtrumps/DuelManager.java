package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Stat;
import com.jrpetty.mobtrumps.game.WagerTable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    /** How long a player has to pick before their turn is auto-played. */
    private static final long TURN_MS = 7_000L;
    /**
     * The client holds a post-result state briefly so the card flip can play
     * before the next prompt lands, so its timer bar starts that much later
     * than the server's. Bank the same grace here or a short turn would expire
     * while the bar still looks part-full.
     */
    private static final long SCREEN_HOLD_MS = 1_900L;

    /** target UUID -> pending challenge from a challenger. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /** player UUID -> the duel they are in (both players map to the same duel). */
    private static final Map<UUID, Duel> ACTIVE = new ConcurrentHashMap<>();
    /** spectator UUID -> the duel they are watching. */
    private static final Map<UUID, Duel> SPECTATING = new ConcurrentHashMap<>();
    /** Scratch set reused by the timer tick, so it allocates nothing per tick. */
    private static final Set<Duel> TICK_SEEN = new HashSet<>();
    /** The same trick for the wager-table sweep: both seats key to one table. */
    private static final Set<Haggle> HAGGLE_SEEN = new HashSet<>();
    /** players waiting to be auto-matched into a duel. */
    private static final Set<UUID> QUEUE = ConcurrentHashMap.newKeySet();
    /** player UUID -> their most recent opponent, for /mobtrumps rematch. */
    private static final Map<UUID, LastFoe> LAST_FOE = new ConcurrentHashMap<>();

    private DuelManager() {
    }

    /**
     * A challenge waiting for an answer.
     *
     * <p>Nothing is escrowed here, which is why {@code cardWager} is a flag and
     * {@code askingPrice} is only an opening ask. Both stakes are collected in
     * one go at the moment the wager table settles — until then either player
     * can walk away having paid nothing, which is the whole point of letting
     * them haggle over the price in the first place.
     */
    private record Pending(UUID challenger, long expiresAt, boolean cardWager,
                           int askingPrice, int bestOf) {
    }

    private record LastFoe(UUID id, String name) {
    }

    private record SideBet(UUID on, int amount) {
    }

    private static final class Duel {
        final ServerPlayer challenger; // PLAYER side
        final ServerPlayer target;     // CPU side
        Battle battle;
        ItemStack challengerWager = ItemStack.EMPTY;
        ItemStack targetWager = ItemStack.EMPTY;
        int challengerBet = 0;
        int targetBet = 0;
        final int bestOf;            // 1, 3 or 5 games per match
        int challengerGames = 0;     // games won so far in a best-of series
        int targetGames = 0;
        final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
        final Map<UUID, SideBet> sideBets = new ConcurrentHashMap<>();
        long turnDeadline = Long.MAX_VALUE;
        boolean warned = false;

        Duel(ServerPlayer challenger, ServerPlayer target, Battle battle, int bestOf) {
            this.challenger = challenger;
            this.target = target;
            this.battle = battle;
            this.bestOf = bestOf;
        }

        /** Games needed to win the match (2 for bo3, 3 for bo5, 1 for a single). */
        int gamesToWin() {
            return bestOf / 2 + 1;
        }

        boolean isSeries() {
            return bestOf > 1;
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
        return challenge(challenger, target, false, 1);
    }

    public static int challenge(ServerPlayer challenger, ServerPlayer target, boolean wager) {
        return challenge(challenger, target, wager, 1);
    }

    /** Challenge with an explicit best-of series length (1, 3 or 5). */
    public static int challenge(ServerPlayer challenger, ServerPlayer target, boolean wager, int bestOf) {
        bestOf = normalizeBestOf(bestOf);
        if (challenger.getUUID().equals(target.getUUID())) {
            challenger.sendSystemMessage(err("You can't duel yourself."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            challenger.sendSystemMessage(err("Someone is already in a duel."));
            return 0;
        }

        // Checked but NOT taken: the card only leaves your hand once the other
        // player has agreed the terms. Re-checked when the table settles, so
        // putting it away in the meantime costs you the duel, not the card.
        if (wager && MobCardItem.cardOf(challenger.getMainHandItem()) == null) {
            challenger.sendSystemMessage(err("Hold the mob card you want to wager."));
            return 0;
        }

        Pending challenge = new Pending(challenger.getUUID(),
                System.currentTimeMillis() + CHALLENGE_TTL_MS, wager, 0, bestOf);
        PENDING.put(target.getUUID(), challenge);
        showInvite(target, challenger, termsFor(challenge));

        String series = bestOf > 1 ? " (best of " + bestOf + ")" : "";
        Component wagerNote = !wager ? Component.literal(series).withStyle(ChatFormatting.AQUA)
                : Component.literal(" wagering ").withStyle(ChatFormatting.GRAY)
                        .append(challenger.getMainHandItem().getHoverName())
                        .append(Component.literal(series).withStyle(ChatFormatting.AQUA));
        challenger.sendSystemMessage(Component.literal("Challenge sent to " + name(target) + ".")
                .withStyle(ChatFormatting.GREEN).append(wagerNote));
        target.sendSystemMessage(Component.literal(name(challenger)
                        + (!wager ? " challenges you to a Mob Trumps duel" + series + "! "
                                  : " challenges you to a WAGER duel" + series + "! "))
                .withStyle(ChatFormatting.GOLD)
                .append(!wager ? Component.empty()
                        : Component.literal("Both stake the card in hand — you'll agree the rest at the table. ")
                                .withStyle(ChatFormatting.GRAY))
                .append(BattleCommands.button("[Accept]", "/mobtrumps duel accept",
                        ChatFormatting.GREEN, "Accept the duel"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps duel decline",
                        ChatFormatting.RED, "Decline the duel")));
        return 1;
    }

    /**
     * Challenge with an emerald price: both players stake it, winner takes the pot.
     *
     * <p>{@code bet} is an opening ask, not a demand — it is the number the
     * wager table opens on, and either player can move it from there before
     * anybody's emeralds are touched.
     */
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
        // Checked, not taken. Emeralds move when the table settles.
        if (countEmeralds(challenger) < bet) {
            challenger.sendSystemMessage(err("You need " + bet + " emeralds to stake that bet (you have "
                    + countEmeralds(challenger) + ")."));
            return 0;
        }

        Pending challenge = new Pending(challenger.getUUID(),
                System.currentTimeMillis() + CHALLENGE_TTL_MS, false,
                WagerTable.clamp(bet), 1);
        PENDING.put(target.getUUID(), challenge);
        showInvite(target, challenger, termsFor(challenge));

        challenger.sendSystemMessage(Component.literal("Challenge sent to " + name(target) + " — staking ")
                .withStyle(ChatFormatting.GREEN)
                .append(emeralds(bet)));
        target.sendSystemMessage(Component.literal(name(challenger) + " challenges you to a WAGER duel! ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("They open at ").withStyle(ChatFormatting.GRAY))
                .append(emeralds(bet))
                .append(Component.literal(" — accept and you can haggle the price. ")
                        .withStyle(ChatFormatting.GRAY))
                .append(BattleCommands.button("[Accept]", "/mobtrumps duel accept",
                        ChatFormatting.GREEN, "Sit down at the wager table"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps duel decline",
                        ChatFormatting.RED, "Decline the duel")));
        return 1;
    }

    public static int accept(ServerPlayer target) {
        clearInvite(target);
        Pending pending = PENDING.remove(target.getUUID());
        if (pending == null) {
            target.sendSystemMessage(err("You have no pending duel challenge."));
            return 0;
        }
        ServerPlayer challenger = target.serverLevel().getServer().getPlayerList().getPlayer(pending.challenger());
        if (pending.expiresAt() < System.currentTimeMillis()) {
            target.sendSystemMessage(err("That duel challenge has expired."));
            return 0;
        }
        if (challenger == null) {
            target.sendSystemMessage(err("The challenger is no longer online."));
            return 0;
        }
        if (isInDuel(challenger) || isInDuel(target)) {
            target.sendSystemMessage(err("Someone is already in a duel."));
            return 0;
        }

        // Accepting is agreeing to PLAY, not agreeing to a price. That gets
        // settled between them at the table, where they can both move it.
        openWager(challenger, target, pending);
        return 1;
    }

    private static int normalizeBestOf(int bestOf) {
        return (bestOf == 3 || bestOf == 5) ? bestOf : 1;
    }

    // --- the wager table: agreeing what the duel is worth ---

    /**
     * Two players who have agreed to play, working out what they are playing
     * for. Neither has paid anything to be here and either can get up and go.
     */
    private static final class Haggle {
        final ServerPlayer a;          // the challenger, seat A
        final ServerPlayer b;          // the one they challenged, seat B
        final WagerTable table;
        final boolean cardWager;       // both also stake the card in hand
        final int bestOf;
        long expiresAt;
        long lastSync;                 // for the once-a-second refresh

        Haggle(ServerPlayer a, ServerPlayer b, int openingPrice, boolean cardWager, int bestOf) {
            this.a = a;
            this.b = b;
            this.table = new WagerTable(openingPrice);
            this.cardWager = cardWager;
            this.bestOf = bestOf;
            this.expiresAt = System.currentTimeMillis() + HAGGLE_TTL_MS;
        }

        ServerPlayer seat(int which) {
            return which == WagerTable.SEAT_A ? a : b;
        }

        int seatOf(ServerPlayer player) {
            if (player.getUUID().equals(a.getUUID())) return WagerTable.SEAT_A;
            if (player.getUUID().equals(b.getUUID())) return WagerTable.SEAT_B;
            return -1;
        }
    }

    /** Both players of a haggle map to the same one, as with ACTIVE. */
    private static final Map<UUID, Haggle> HAGGLING = new ConcurrentHashMap<>();

    /**
     * Longer than a challenge gets, because this one is a conversation. Still
     * bounded: a table nobody is talking at should not hold two players out of
     * other duels forever.
     */
    private static final long HAGGLE_TTL_MS = 180_000L;

    /** Sit both players down at a wager table opened on the challenger's ask. */
    private static void openWager(ServerPlayer challenger, ServerPlayer target, Pending pending) {
        Haggle haggle = new Haggle(challenger, target, pending.askingPrice(),
                pending.cardWager(), normalizeBestOf(pending.bestOf()));
        HAGGLING.put(challenger.getUUID(), haggle);
        HAGGLING.put(target.getUUID(), haggle);
        target.sendSystemMessage(Component.literal("You sit down with " + name(challenger)
                + " to agree the stakes.").withStyle(ChatFormatting.GOLD));
        challenger.sendSystemMessage(Component.literal(name(target)
                + " accepted — agree the stakes and you're on.").withStyle(ChatFormatting.GOLD));
        syncWager(haggle);
    }

    /** Handle one click at the wager table. */
    public static void handleWagerAction(ServerPlayer player, int action, int value) {
        Haggle haggle = HAGGLING.get(player.getUUID());
        if (haggle == null) {
            return;
        }
        int seat = haggle.seatOf(player);
        if (seat < 0) {
            return;
        }
        ServerPlayer other = haggle.seat(WagerTable.otherSeat(seat));
        switch (action) {
            case DuelWagerActionPayload.PROPOSE -> {
                if (haggle.table.propose(seat, value)) {
                    other.sendSystemMessage(Component.literal(name(player) + " wants ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(priceWords(haggle.table.price())));
                    clickBoth(haggle);
                }
            }
            case DuelWagerActionPayload.AGREE -> {
                int price = haggle.table.price();
                if (price > 0 && countEmeralds(player) < price) {
                    player.sendSystemMessage(err("You can't cover " + price + " emeralds (you have "
                            + countEmeralds(player) + ")."));
                    break;
                }
                if (haggle.cardWager && MobCardItem.cardOf(player.getMainHandItem()) == null) {
                    player.sendSystemMessage(err("Hold the mob card you're staking, then agree."));
                    break;
                }
                if (haggle.table.agree(seat)) {
                    if (haggle.table.settled()) {
                        dealHaggled(haggle);
                    } else {
                        other.sendSystemMessage(Component.literal(name(player) + " agrees to ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(priceWords(haggle.table.price())));
                        clickBoth(haggle);
                    }
                }
            }
            case DuelWagerActionPayload.REOPEN -> {
                if (haggle.table.reopen()) {
                    sendBothHaggle(haggle, Component.literal(name(player)
                                    + " put the price back up for discussion.")
                            .withStyle(ChatFormatting.YELLOW));
                    clickBoth(haggle);
                }
            }
            case DuelWagerActionPayload.LEAVE -> closeWager(haggle,
                    Component.literal(name(player) + " walked away from the table — nothing was staked.")
                            .withStyle(ChatFormatting.GRAY));
            default -> {
            }
        }
    }

    /**
     * Both players agreed. Take the stakes — checking that BOTH are still good
     * for them before either one is touched — and deal.
     *
     * <p>The gap between agreeing and paying is small but it is not zero: a
     * player can spend their emeralds or put their card away in it. Taking one
     * side's stake and then discovering the other cannot pay would leave the
     * first player robbed by a duel that never happened.
     */
    private static void dealHaggled(Haggle haggle) {
        int price = haggle.table.price();
        ServerPlayer a = haggle.a;
        ServerPlayer b = haggle.b;

        if (price > 0) {
            ServerPlayer broke = countEmeralds(a) < price ? a : (countEmeralds(b) < price ? b : null);
            if (broke != null) {
                haggle.table.reopen();
                sendBothHaggle(haggle, Component.literal(name(broke) + " can no longer cover "
                        + price + " emeralds — agree a new price.").withStyle(ChatFormatting.RED));
                syncWager(haggle);
                return;
            }
        }
        ItemStack cardA = ItemStack.EMPTY;
        ItemStack cardB = ItemStack.EMPTY;
        if (haggle.cardWager) {
            ItemStack heldA = a.getMainHandItem();
            ItemStack heldB = b.getMainHandItem();
            ServerPlayer empty = MobCardItem.cardOf(heldA) == null ? a
                    : (MobCardItem.cardOf(heldB) == null ? b : null);
            if (empty != null) {
                haggle.table.reopen();
                sendBothHaggle(haggle, Component.literal(name(empty)
                                + " isn't holding a card any more — agree again when they are.")
                        .withStyle(ChatFormatting.RED));
                syncWager(haggle);
                return;
            }
            cardA = heldA.copyWithCount(1);
            cardB = heldB.copyWithCount(1);
            heldA.shrink(1);
            heldB.shrink(1);
        }
        if (price > 0) {
            takeEmeralds(a, price);
            takeEmeralds(b, price);
        }

        forgetHaggle(haggle);
        closeWagerScreens(haggle);
        if (price > 0 || haggle.cardWager) {
            sendPair(a, b, Component.literal("Agreed: ").withStyle(ChatFormatting.GOLD)
                    .append(priceWords(price))
                    .append(Component.literal(haggle.cardWager
                            ? " and the card in hand, each." : " each.").withStyle(ChatFormatting.GRAY)));
        }
        startDuel(a, b, cardA, cardB, price, price, haggle.bestOf);
    }

    /** "250 emeralds" / "nothing at all", for a chat line. */
    private static Component priceWords(int price) {
        return price <= 0
                ? Component.literal("a friendly game").withStyle(ChatFormatting.AQUA)
                : emeralds(price);
    }

    /**
     * Drop the table and tell both players why, once. Both of them are keyed to
     * the same Haggle, so whichever entry is still there decides whether this
     * close is the real one or a second call about a table that has already
     * gone — a timeout landing on the same tick as a walk-out, say.
     */
    private static void closeWager(Haggle haggle, Component why) {
        if (!forgetHaggle(haggle)) {
            return;
        }
        closeWagerScreens(haggle);
        sendBothHaggle(haggle, why);
    }

    /** Remove the table from both seats. False if it had already gone. */
    private static boolean forgetHaggle(Haggle haggle) {
        boolean hadA = HAGGLING.remove(haggle.a.getUUID()) != null;
        boolean hadB = HAGGLING.remove(haggle.b.getUUID()) != null;
        return hadA || hadB;
    }

    /** Take the table off both screens. */
    private static void closeWagerScreens(Haggle haggle) {
        for (ServerPlayer p : new ServerPlayer[]{haggle.a, haggle.b}) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, DuelWagerPayload.closed());
        }
    }

    /** Push the table's state to both players, each from their own side of it. */
    private static void syncWager(Haggle haggle) {
        int seconds = (int) Math.max(0L, (haggle.expiresAt - System.currentTimeMillis() + 999L) / 1000L);
        for (int seat : new int[]{WagerTable.SEAT_A, WagerTable.SEAT_B}) {
            ServerPlayer me = haggle.seat(seat);
            ServerPlayer them = haggle.seat(WagerTable.otherSeat(seat));
            int price = haggle.table.price();
            int mover = haggle.table.lastMover() == WagerTable.NOBODY ? DuelWagerPayload.MOVER_NOBODY
                    : (haggle.table.lastMover() == seat ? DuelWagerPayload.MOVER_YOU
                                                        : DuelWagerPayload.MOVER_THEM);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(me,
                    new DuelWagerPayload(DuelWagerPayload.OPEN, name(them),
                            heldCardId(haggle, me), heldCardId(haggle, them),
                            java.util.List.of(
                            price,
                            haggle.table.agreed(seat) ? 1 : 0,
                            haggle.table.agreed(WagerTable.otherSeat(seat)) ? 1 : 0,
                            countEmeralds(me),
                            seconds,
                            haggle.bestOf,
                            haggle.cardWager ? 1 : 0,
                            mover,
                            countEmeralds(them) >= price ? 1 : 0)));
        }
    }

    /**
     * The card this player would be staking, for the table to show — only in a
     * card wager, where the held card genuinely is on the line. In an emerald
     * game what somebody happens to be holding is their business.
     */
    private static String heldCardId(Haggle haggle, ServerPlayer player) {
        if (!haggle.cardWager) {
            return "";
        }
        MobCard card = MobCardItem.cardOf(player.getMainHandItem());
        return card == null ? "" : card.id();
    }

    /** Sync both screens and give both players the click that says it landed. */
    private static void clickBoth(Haggle haggle) {
        syncWager(haggle);
        for (ServerPlayer p : new ServerPlayer[]{haggle.a, haggle.b}) {
            p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.1f);
        }
    }

    private static void sendBothHaggle(Haggle haggle, Component message) {
        sendPair(haggle.a, haggle.b, message);
    }

    private static void sendPair(ServerPlayer a, ServerPlayer b, Component message) {
        a.sendSystemMessage(message);
        b.sendSystemMessage(message);
    }

    /**
     * Close tables nobody settled in time.
     *
     * <p>Both seats key to the same Haggle, so the values are de-duplicated
     * before iterating or a timeout would try to close each table twice.
     */
    private static void expireHaggles() {
        if (HAGGLING.isEmpty()) return;
        long now = System.currentTimeMillis();
        // reuse one scratch set rather than allocating a fresh one every tick,
        // the same way the duel timer sweep above does
        Set<Haggle> seen = HAGGLE_SEEN;
        seen.clear();
        seen.addAll(HAGGLING.values());
        for (Haggle haggle : seen) {
            if (haggle.expiresAt <= now) {
                closeWager(haggle, Component.literal("Nobody agreed a price — the duel is off.")
                        .withStyle(ChatFormatting.GRAY));
            } else if (now - haggle.lastSync >= 1000L) {
                // once a second, so the cards on the table track what is
                // actually in each hand and neither clock drifts far
                haggle.lastSync = now;
                syncWager(haggle);
            }
        }
    }

    /** A player leaving mid-negotiation ends it, and costs neither of them anything. */
    private static void abandonHaggle(ServerPlayer player) {
        Haggle haggle = HAGGLING.get(player.getUUID());
        if (haggle != null) {
            closeWager(haggle, Component.literal(name(player)
                            + " left before a price was agreed — nothing was staked.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Start an unwagered duel from a dueling table block at the seat's chosen length. */
    public static void startFromTable(ServerPlayer challenger, ServerPlayer target, int bestOf) {
        if (isInDuel(challenger) || isInDuel(target)) {
            target.sendSystemMessage(err(name(isInDuel(challenger) ? challenger : target)
                    + " is already in a duel."));
            return;
        }
        startDuel(challenger, target, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, normalizeBestOf(bestOf));
    }

    /** Create and kick off a duel between two players once any stakes are escrowed. */
    private static void startDuel(ServerPlayer challenger, ServerPlayer target,
                                  ItemStack chWager, ItemStack tgWager, int chBet, int tgBet, int bestOf) {
        QUEUE.remove(challenger.getUUID());
        QUEUE.remove(target.getUUID());
        Battle battle = new Battle(DECK_SIZE, ThreadLocalRandom.current());
        Duel duel = new Duel(challenger, target, battle, normalizeBestOf(bestOf));
        duel.challengerWager = chWager;
        duel.targetWager = tgWager;
        duel.challengerBet = chBet;
        duel.targetBet = tgBet;
        ACTIVE.put(challenger.getUUID(), duel);
        ACTIVE.put(target.getUUID(), duel);
        LAST_FOE.put(challenger.getUUID(), new LastFoe(target.getUUID(), name(target)));
        LAST_FOE.put(target.getUUID(), new LastFoe(challenger.getUUID(), name(challenger)));

        String series = duel.isSeries() ? " (best of " + duel.bestOf + ")" : "";
        MutableComponent intro = Component.literal("=== DUEL: " + name(challenger)
                + " vs " + name(target) + series + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        challenger.sendSystemMessage(intro);
        target.sendSystemMessage(intro);
        if (duel.isSeries()) {
            sendBoth(duel, Component.literal("First to " + duel.gamesToWin() + " games wins the match.")
                    .withStyle(ChatFormatting.AQUA));
        }
        sendBoth(duel, emoteBar());
        BattleCommands.shuffleSound(challenger);
        BattleCommands.shuffleSound(target);
        promptTurn(duel);
    }

    /** Start a duel with pre-drafted hands (no wagers) — used by draft mode. */
    public static void startDraftDuel(ServerPlayer a, ServerPlayer b,
                                      java.util.List<com.jrpetty.mobtrumps.game.MobCard> handA,
                                      java.util.List<com.jrpetty.mobtrumps.game.MobCard> handB) {
        if (isInDuel(a) || isInDuel(b)) return;
        QUEUE.remove(a.getUUID());
        QUEUE.remove(b.getUUID());
        Battle battle = new Battle(handA, handB, ThreadLocalRandom.current());
        Duel duel = new Duel(a, b, battle, 1);
        ACTIVE.put(a.getUUID(), duel);
        ACTIVE.put(b.getUUID(), duel);
        LAST_FOE.put(a.getUUID(), new LastFoe(b.getUUID(), name(b)));
        LAST_FOE.put(b.getUUID(), new LastFoe(a.getUUID(), name(a)));
        MutableComponent intro = Component.literal("=== DRAFT DUEL: " + name(a)
                + " vs " + name(b) + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        a.sendSystemMessage(intro);
        b.sendSystemMessage(intro);
        sendBoth(duel, emoteBar());
        BattleCommands.shuffleSound(a);
        BattleCommands.shuffleSound(b);
        promptTurn(duel);
    }

    public static int decline(ServerPlayer target) {
        clearInvite(target);
        Pending pending = PENDING.remove(target.getUUID());
        if (pending == null) {
            target.sendSystemMessage(err("You have no pending duel challenge."));
            return 0;
        }
        // nothing to hand back: a challenge escrows nothing any more
        ServerPlayer challenger = target.serverLevel().getServer().getPlayerList().getPlayer(pending.challenger());
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

    /**
     * Committed to a duel — either playing one or still agreeing its price.
     *
     * <p>Every caller is a guard ("can this player be challenged / queued /
     * drafted / rematched right now"), and the answer for someone sat at a
     * wager table is no. Code that needs the live battle itself reads ACTIVE
     * directly rather than asking this.
     */
    public static boolean isInDuel(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID()) || HAGGLING.containsKey(player.getUUID());
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
        StatsTracker.recordPick(player, stat);
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
        QUEUE.remove(player.getUUID());
        // if they were spectating, stop and refund any side bet
        Duel watched = SPECTATING.remove(player.getUUID());
        if (watched != null) {
            watched.spectators.remove(player.getUUID());
            SideBet bet = watched.sideBets.remove(player.getUUID());
            if (bet != null) giveEmeralds(player, bet.amount());
        }
        // a wager still being argued over: nothing was staked, so it just ends
        abandonHaggle(player);
        // pending challenge TO this player — nothing escrowed, just drop it
        PENDING.remove(player.getUUID());
        // pending challenge FROM this player: cancel it, and take the now-dead
        // invite off whoever it was pointed at
        PENDING.entrySet().removeIf(e -> {
            if (e.getValue().challenger().equals(player.getUUID())) {
                ServerPlayer invited = player.serverLevel().getServer()
                        .getPlayerList().getPlayer(e.getKey());
                clearInvite(invited);
                if (invited != null) {
                    invited.sendSystemMessage(Component.literal(name(player)
                            + " left — their challenge is off.").withStyle(ChatFormatting.GRAY));
                }
                return true;
            }
            return false;
        });

        Duel duel = ACTIVE.get(player.getUUID());
        if (duel != null) {
            ServerPlayer other = duel.other(player);
            settleSideBets(duel, other);
            clear(duel);
            pushFinished(duel, other); // close out the remaining player's battle screen
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

    // --- matchmaking / spectating / emotes / rematch ---

    /** Join the auto-match queue, pairing instantly if someone else is waiting. */
    public static int queue(ServerPlayer player) {
        if (isInDuel(player)) {
            player.sendSystemMessage(err("Finish your current duel first."));
            return 0;
        }
        if (QUEUE.contains(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You're already queued. ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(BattleCommands.button("[Leave]", "/mobtrumps queue leave",
                            ChatFormatting.RED, "Leave the matchmaking queue")));
            return 0;
        }
        MinecraftServer server = player.getServer();
        for (UUID id : QUEUE) {
            ServerPlayer other = server == null ? null : server.getPlayerList().getPlayer(id);
            if (other != null && !other.getUUID().equals(player.getUUID()) && !isInDuel(other)) {
                QUEUE.remove(id);
                Component found = Component.literal("Match found — duel starting!")
                        .withStyle(ChatFormatting.GREEN);
                player.sendSystemMessage(found);
                other.sendSystemMessage(found);
                startDuel(other, player, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, 1);
                return 1;
            }
        }
        QUEUE.add(player.getUUID());
        player.sendSystemMessage(Component.literal("Searching for an opponent... ")
                .withStyle(ChatFormatting.AQUA)
                .append(BattleCommands.button("[Leave]", "/mobtrumps queue leave",
                        ChatFormatting.RED, "Leave the matchmaking queue")));
        return 1;
    }

    public static int leaveQueue(ServerPlayer player) {
        if (QUEUE.remove(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Left the duel queue.").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(err("You're not in the queue."));
        }
        return 1;
    }

    /** Re-challenge your most recent opponent. */
    public static int rematch(ServerPlayer player) {
        if (isInDuel(player)) {
            player.sendSystemMessage(err("You're already in a duel."));
            return 0;
        }
        LastFoe foe = LAST_FOE.get(player.getUUID());
        if (foe == null) {
            player.sendSystemMessage(err("You have no recent opponent to rematch."));
            return 0;
        }
        MinecraftServer server = player.getServer();
        ServerPlayer other = server == null ? null : server.getPlayerList().getPlayer(foe.id());
        if (other == null) {
            player.sendSystemMessage(err(foe.name() + " is offline."));
            return 0;
        }
        return challenge(player, other);
    }

    public static int watch(ServerPlayer viewer, ServerPlayer duelist) {
        if (isInDuel(viewer)) {
            viewer.sendSystemMessage(err("You can't spectate while in your own duel."));
            return 0;
        }
        Duel duel = ACTIVE.get(duelist.getUUID());
        if (duel == null) {
            viewer.sendSystemMessage(err(name(duelist) + " isn't in a duel right now."));
            return 0;
        }
        Duel previous = SPECTATING.get(viewer.getUUID());
        if (previous != null && previous != duel) {
            previous.spectators.remove(viewer.getUUID());
        }
        duel.spectators.add(viewer.getUUID());
        SPECTATING.put(viewer.getUUID(), duel);
        viewer.sendSystemMessage(Component.literal("Now spectating " + name(duel.challenger)
                        + " vs " + name(duel.target) + ". ").withStyle(ChatFormatting.GREEN)
                .append(BattleCommands.button("[Stop]", "/mobtrumps unwatch",
                        ChatFormatting.RED, "Stop spectating")));
        viewer.sendSystemMessage(Component.literal("Side bet 5 emeralds: ").withStyle(ChatFormatting.GRAY)
                .append(BattleCommands.button("[on " + name(duel.challenger) + "]",
                        "/mobtrumps sidebet " + name(duel.challenger) + " 5", ChatFormatting.GOLD,
                        "Bet 5 emeralds on " + name(duel.challenger)))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[on " + name(duel.target) + "]",
                        "/mobtrumps sidebet " + name(duel.target) + " 5", ChatFormatting.GOLD,
                        "Bet 5 emeralds on " + name(duel.target))));
        return 1;
    }

    public static int unwatch(ServerPlayer viewer) {
        Duel duel = SPECTATING.remove(viewer.getUUID());
        if (duel == null) {
            viewer.sendSystemMessage(err("You're not spectating anything."));
            return 0;
        }
        duel.spectators.remove(viewer.getUUID());
        SideBet bet = duel.sideBets.remove(viewer.getUUID());
        if (bet != null) {
            giveEmeralds(viewer, bet.amount());
            viewer.sendSystemMessage(Component.literal("Stopped spectating — side bet refunded.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            viewer.sendSystemMessage(Component.literal("Stopped spectating.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    public static int sideBet(ServerPlayer viewer, ServerPlayer on, int amount) {
        Duel duel = SPECTATING.get(viewer.getUUID());
        if (duel == null) {
            viewer.sendSystemMessage(err("Watch a duel first: /mobtrumps watch <player>."));
            return 0;
        }
        boolean valid = on.getUUID().equals(duel.challenger.getUUID())
                || on.getUUID().equals(duel.target.getUUID());
        if (!valid) {
            viewer.sendSystemMessage(err("You can only bet on one of the two duelists."));
            return 0;
        }
        if (duel.sideBets.containsKey(viewer.getUUID())) {
            viewer.sendSystemMessage(err("You already have a side bet on this duel."));
            return 0;
        }
        if (amount <= 0) {
            viewer.sendSystemMessage(err("Bet at least 1 emerald."));
            return 0;
        }
        if (countEmeralds(viewer) < amount) {
            viewer.sendSystemMessage(err("You need " + amount + " emeralds."));
            return 0;
        }
        takeEmeralds(viewer, amount);
        duel.sideBets.put(viewer.getUUID(), new SideBet(on.getUUID(), amount));
        sendBoth(duel, Component.literal(name(viewer) + " bet ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(emeralds(amount))
                .append(Component.literal(" on " + name(on) + "!").withStyle(ChatFormatting.LIGHT_PURPLE)));
        return 1;
    }

    public static int emote(ServerPlayer player, String key) {
        Duel duel = ACTIVE.get(player.getUUID());
        if (duel == null) {
            player.sendSystemMessage(err("Emotes are for during a duel."));
            return 0;
        }
        String text = switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "gg" -> "gg!";
            case "nice" -> "Nice one!";
            case "close" -> "So close!";
            case "oops" -> "Oops...";
            case "gl" -> "Good luck!";
            case "wow" -> "Wow!";
            default -> null;
        };
        if (text == null) {
            player.sendSystemMessage(err("Unknown emote. Try: gg, nice, close, oops, gl, wow."));
            return 0;
        }
        sendBoth(duel, Component.literal(name(player) + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static Component emoteBar() {
        MutableComponent bar = Component.literal("Emotes: ").withStyle(ChatFormatting.DARK_GRAY);
        for (String k : new String[]{"gg", "nice", "close", "oops", "gl", "wow"}) {
            bar.append(BattleCommands.button("[" + k + "]", "/mobtrumps emote " + k,
                    ChatFormatting.YELLOW, "Say \"" + k + "\""));
            bar.append(Component.literal(" "));
        }
        return bar;
    }

    /** Auto-play any duel whose per-turn timer has expired (called every server tick). */
    public static void tickTimers(MinecraftServer server) {
        expirePending(server);
        expireHaggles();
        if (ACTIVE.isEmpty()) return;
        long now = System.currentTimeMillis();
        // both duellists map to the same Duel, so we still need to de-duplicate —
        // but reuse one scratch set rather than allocating one every tick
        Set<Duel> seen = TICK_SEEN;
        seen.clear();
        for (Duel duel : ACTIVE.values()) {
            if (!seen.add(duel) || duel.battle.isFinished()) continue;
            long left = duel.turnDeadline - now;
            if (left <= 0) {
                autoPlay(duel);
            }
            // no chat warning: turns are short and the on-screen timer bar
            // flashes red on its own as the clock runs down
        }
    }

    /**
     * Drop challenges nobody answered inside the minute: hand the stake back and
     * take the invite off the screen.
     *
     * <p>Nothing is escrowed by a challenge any more, so there is no stake to
     * hand back — but a challenge that never dies still blocks both players out
     * of other duels, and now that answering is a button that disappears with
     * the clock, nobody would ever clear it by hand.
     */
    private static void expirePending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> {
            Pending pending = entry.getValue();
            if (pending.expiresAt() > now) return false;
            ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer challenger = server.getPlayerList().getPlayer(pending.challenger());
            clearInvite(target);
            if (challenger != null) {
                challenger.sendSystemMessage(Component.literal(
                                (target != null ? name(target) : "They") + " didn't answer — challenge expired.")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (target != null) {
                target.sendSystemMessage(Component.literal("The duel challenge expired.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return true;
        });
    }

    private static void autoPlay(Duel duel) {
        Battle.Side turn = duel.battle.getTurn();
        MobCard top = turn == Battle.Side.PLAYER ? duel.battle.playerTopCard() : duel.battle.cpuTopCard();
        if (top == null) {
            duel.turnDeadline = Long.MAX_VALUE;
            return;
        }
        Stat stat = top.bestStat();
        sendBoth(duel, Component.literal(name(duel.forSide(turn)) + " ran out of time — auto-playing "
                + stat.label + ".").withStyle(ChatFormatting.RED));
        resolveRound(duel, stat);
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

        // duelists watch the round unfold on the battle screen; only spectators
        // (who have no screen) get the play-by-play in chat
        sendSpectators(duel, reveal);
        sendSpectators(duel, outcome);
        pushResult(duel, result); // flip & reveal on both players' battle screens

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
            finishGame(duel);
        } else {
            promptTurn(duel);
        }
    }

    // --- on-screen battle sync: mirror the chat duel onto both BattleScreens ---

    /** Emote keys in the order the battle screen's emote wheel sends them. */
    private static final String[] SCREEN_EMOTES = {"gg", "gl", "nice", "close", "oops", "wow"};

    // on-screen rematch: the best-of mode of each player's last duel, and who has
    // an open rematch offer (reuses the existing LAST_FOE map for the opponent)
    private static final Map<UUID, Integer> LAST_MODE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REMATCH_WANT = new ConcurrentHashMap<>();
    private static final long REMATCH_TTL_MS = 30_000L;

    public static void handleScreenAction(ServerPlayer player, int action, int stat) {
        switch (action) {
            case BattleActionPayload.PICK -> {
                Stat[] all = Stat.values();
                if (stat >= 0 && stat < all.length) {
                    play(player, all[stat].key());
                }
            }
            case BattleActionPayload.FORFEIT -> forfeit(player);
            case BattleActionPayload.EMOTE -> screenEmote(player, stat);
            default -> {
            }
        }
    }

    /** REMATCH arrives after the duel has ended, so it's routed here directly.
     *  When both players have offered, a fresh unwagered duel starts at once. */
    public static void handleScreenRematch(ServerPlayer player) {
        LastFoe foe = LAST_FOE.get(player.getUUID());
        if (foe == null) {
            return;
        }
        ServerPlayer opp = player.serverLevel().getServer().getPlayerList().getPlayer(foe.id());
        if (opp == null) {
            player.displayClientMessage(Component.literal("Your last opponent has left.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (isInDuel(player) || isInDuel(opp) || DraftManager.isDrafting(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long theirWant = REMATCH_WANT.get(foe.id());
        LastFoe theirFoe = LAST_FOE.get(foe.id());
        boolean mutual = theirWant != null && theirWant > now
                && theirFoe != null && player.getUUID().equals(theirFoe.id());
        if (mutual) {
            REMATCH_WANT.remove(player.getUUID());
            REMATCH_WANT.remove(foe.id());
            int mode = LAST_MODE.getOrDefault(player.getUUID(), 1);
            startFromTable(player, opp, mode); // fresh, unwagered rematch
        } else {
            REMATCH_WANT.put(player.getUUID(), now + REMATCH_TTL_MS);
            pushEmote(opp, 1, name(player) + " wants a rematch!");
            player.displayClientMessage(Component.literal("Rematch offered — waiting for "
                    + name(opp) + "...").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void screenEmote(ServerPlayer player, int idx) {
        Duel duel = ACTIVE.get(player.getUUID());
        if (duel == null || idx < 0 || idx >= SCREEN_EMOTES.length) {
            return;
        }
        String text = emoteText(SCREEN_EMOTES[idx]);
        ServerPlayer opp = duel.other(player);
        pushEmote(player, 0, text); // above your own card
        pushEmote(opp, 1, text);    // above your opponent's card on their screen
        sendSpectators(duel, Component.literal(name(player) + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    private static void pushEmote(ServerPlayer to, int side, String text) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(to,
                new BattleEmotePayload(side, text));
    }

    private static String emoteText(String key) {
        return switch (key) {
            case "gg" -> "gg!";
            case "nice" -> "Nice one!";
            case "close" -> "So close!";
            case "oops" -> "Oops...";
            case "gl" -> "Good luck!";
            case "wow" -> "Wow!";
            default -> key;
        };
    }

    private static void pushTurn(Duel duel) {
        Battle.Side turn = duel.battle.getTurn();
        for (ServerPlayer p : new ServerPlayer[]{duel.challenger, duel.target}) {
            Battle.Side side = duel.sideOf(p);
            int phase = side == turn ? BattleSyncPayload.PLAYER_PICK : BattleSyncPayload.OPPONENT_PICK;
            MobCard myTop = side == Battle.Side.PLAYER
                    ? duel.battle.playerTopCard() : duel.battle.cpuTopCard();
            sendScreen(duel, p, phase, myTop == null ? "" : myTop.id(), "", -1, 2, 2);
        }
    }

    private static void pushResult(Duel duel, Battle.RoundResult result) {
        for (ServerPlayer p : new ServerPlayer[]{duel.challenger, duel.target}) {
            Battle.Side side = duel.sideOf(p);
            MobCard mine = side == Battle.Side.PLAYER ? result.playerCard() : result.cpuCard();
            MobCard opp = side == Battle.Side.PLAYER ? result.cpuCard() : result.playerCard();
            int chooser = result.chooser() == side ? 0 : 1;
            int winner = result.winner() == Battle.Side.NONE ? 2 : (result.winner() == side ? 0 : 1);
            sendScreen(duel, p, BattleSyncPayload.RESULT, mine.id(), opp.id(),
                    result.stat().ordinal(), chooser, winner);
        }
    }

    private static void pushFinished(Duel duel, ServerPlayer winner) {
        for (ServerPlayer p : new ServerPlayer[]{duel.challenger, duel.target}) {
            int w = winner == null ? 2 : (winner.getUUID().equals(p.getUUID()) ? 0 : 1);
            sendScreen(duel, p, BattleSyncPayload.FINISHED, "", "", -1, 2, w);
        }
    }

    /**
     * PvP hands are never upgraded — both sides field base prints, so the two
     * holo-level slots the battle screen reads (14 and 15) are left off and
     * default to 0. If a duel mode ever starts fielding levelled cards it must
     * send them, or the screen will draw base numbers for a card that was played
     * as a premium print and the round will look wrongly decided.
     */
    private static void sendScreen(Duel duel, ServerPlayer p, int phase, String myId, String oppId,
                                   int chosen, int chooser, int winner) {
        Battle.Side side = duel.sideOf(p);
        int myCount = side == Battle.Side.PLAYER
                ? duel.battle.playerCardCount() : duel.battle.cpuCardCount();
        int oppCount = side == Battle.Side.PLAYER
                ? duel.battle.cpuCardCount() : duel.battle.playerCardCount();
        int myGames = side == Battle.Side.PLAYER ? duel.challengerGames : duel.targetGames;
        int oppGames = side == Battle.Side.PLAYER ? duel.targetGames : duel.challengerGames;
        // the per-turn countdown (seconds) so the screen can draw a timer bar
        int turnSeconds = (phase == BattleSyncPayload.PLAYER_PICK
                || phase == BattleSyncPayload.OPPONENT_PICK) ? (int) (TURN_MS / 1000L) : 0;
        // who won the coin flip on a drawn round, from THIS player's seat
        Battle.Side coinSide = duel.battle.lastCoin();
        int coin = coinSide == Battle.Side.NONE ? 0 : (coinSide == side ? 1 : 2);
        java.util.List<Integer> nums = new java.util.ArrayList<>(java.util.List.of(
                myCount, oppCount, duel.battle.potCount(), duel.battle.getRound(),
                chosen, chooser, winner, 0, 1, myGames, oppGames, turnSeconds, coin));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new BattleSyncPayload(phase, myId, oppId, nums, name(duel.other(p))));
    }

    /** One game (a full Battle) has ended. For a series, tally it and deal the next
     *  game until someone reaches the games needed; otherwise end the match. */
    private static void finishGame(Duel duel) {
        Battle.Side winSide = duel.battle.getWinner();

        if (!duel.isSeries()) {
            if (winSide == Battle.Side.NONE) {
                endDuel(duel, null, null, false);
            } else {
                endDuel(duel, duel.forSide(winSide), duel.forSide(winSide == Battle.Side.PLAYER
                        ? Battle.Side.CPU : Battle.Side.PLAYER), false);
            }
            return;
        }

        // --- best-of series ---
        if (winSide == Battle.Side.NONE) {
            sendSpectators(duel, Component.literal("Game drawn — it doesn't count. Re-dealing...")
                    .withStyle(ChatFormatting.YELLOW));
            dealNextGame(duel);
            return;
        }
        ServerPlayer gameWinner = duel.forSide(winSide);
        if (winSide == Battle.Side.PLAYER) duel.challengerGames++; else duel.targetGames++;

        sendSpectators(duel, Component.literal(name(gameWinner) + " wins the game! Series: ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(name(duel.challenger) + " " + duel.challengerGames
                        + " - " + duel.targetGames + " " + name(duel.target))
                        .withStyle(ChatFormatting.AQUA)));

        int need = duel.gamesToWin();
        if (duel.challengerGames >= need) {
            endDuel(duel, duel.challenger, duel.target, false);
        } else if (duel.targetGames >= need) {
            endDuel(duel, duel.target, duel.challenger, false);
        } else {
            dealNextGame(duel);
        }
    }

    /** Deal a fresh game within an ongoing best-of series. */
    private static void dealNextGame(Duel duel) {
        int gameNo = duel.challengerGames + duel.targetGames + 1;
        duel.battle = new Battle(DECK_SIZE, ThreadLocalRandom.current());
        sendSpectators(duel, Component.literal("--- Game " + gameNo + " of up to " + duel.bestOf + " ---")
                .withStyle(ChatFormatting.GOLD));
        BattleCommands.shuffleSound(duel.challenger);
        BattleCommands.shuffleSound(duel.target);
        promptTurn(duel);
    }


    /**
     * Raise the challenge on the target's screen as well as in their chat.
     *
     * <p>Chat stays because a player mid-game does not get their screen taken
     * over, and because a line they can scroll back to is worth having. The
     * screen is what actually makes an invite noticeable.
     */
    private static void showInvite(ServerPlayer target, ServerPlayer from, String terms) {
        if (target == null) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(target,
                DuelInvitePayload.show(name(from), terms, (int) (CHALLENGE_TTL_MS / 1000L)));
    }

    /** The one-line description of what is being played for, rebuilt from the challenge. */
    private static String termsFor(Pending pending) {
        if (pending.askingPrice() > 0) {
            return "They open at " + pending.askingPrice() + " emeralds";
        }
        if (pending.cardWager()) {
            return "Both stake the card in hand"
                    + (pending.bestOf() > 1 ? " · best of " + pending.bestOf() : "");
        }
        return pending.bestOf() > 1 ? "Best of " + pending.bestOf() : "A friendly game";
    }

    /** Take the challenge off their screen — answered, expired or withdrawn. */
    private static void clearInvite(ServerPlayer target) {
        if (target != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(target,
                    DuelInvitePayload.clear());
        }
    }

    private static void promptTurn(Duel duel) {
        ServerPlayer chooser = duel.forSide(duel.battle.getTurn());

        int challengerCards = duel.battle.playerCardCount();
        int targetCards = duel.battle.cpuCardCount();
        int pot = duel.battle.potCount();
        // in a series, keep the match score in view: "Game 2 (1-0) · "
        String series = duel.isSeries()
                ? "Game " + (duel.challengerGames + duel.targetGames + 1)
                        + " (" + duel.challengerGames + "-" + duel.targetGames + ") · "
                : "";
        // the duelists see cards, tallies and whose turn it is on the screen;
        // only spectators need the chat readout
        MutableComponent score = Component.literal(series
                + name(duel.challenger) + ": " + challengerCards
                + " | " + name(duel.target) + ": " + targetCards
                + (pot > 0 ? " | Pot: " + pot : "")).withStyle(ChatFormatting.DARK_GRAY);
        sendSpectators(duel, score);
        sendSpectators(duel, Component.literal(name(chooser) + " is choosing a stat...")
                .withStyle(ChatFormatting.DARK_GRAY));
        duel.turnDeadline = System.currentTimeMillis() + TURN_MS + SCREEN_HOLD_MS;
        duel.warned = false;
        pushTurn(duel); // drive both battle screens (cards, timer bar, turn)
    }

    private static void endDuel(Duel duel, ServerPlayer winner, ServerPlayer loser, boolean forfeit) {
        settleSideBets(duel, winner);
        clear(duel);
        pushFinished(duel, winner); // final banner on both battle screens
        // remember the mode so an on-screen Rematch re-deals the same series
        // length (LAST_FOE for the opponent is already set at duel start)
        LAST_MODE.put(duel.challenger.getUUID(), duel.bestOf);
        LAST_MODE.put(duel.target.getUUID(), duel.bestOf);
        REMATCH_WANT.remove(duel.challenger.getUUID());
        REMATCH_WANT.remove(duel.target.getUUID());
        // a finished duel counts as a game played for both seats
        StatsTracker.bump(duel.challenger, "games_played");
        StatsTracker.bump(duel.target, "games_played");
        AchievementManager.refresh(duel.challenger);
        AchievementManager.refresh(duel.target);
        boolean wager = duel.isWager();
        if (winner == null) {
            sendBoth(duel, Component.literal("The duel is a draw — every stake is returned.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            // draws return each stake to its owner
            returnStake(duel.challenger, duel.challengerWager);
            returnStake(duel.target, duel.targetWager);
            returnBet(duel.challenger, duel.challengerBet);
            returnBet(duel.target, duel.targetBet);
            sendRematchPrompt(duel);
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
        StatsTracker.recordLossTo(loser, name(winner));
        TournamentManager.onDuelResult(winner, loser);

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
        sendRematchPrompt(duel);
    }

    private static void sendRematchPrompt(Duel duel) {
        Component prompt = Component.literal("Play again? ").withStyle(ChatFormatting.GRAY)
                .append(BattleCommands.button("[Rematch]", "/mobtrumps rematch",
                        ChatFormatting.GREEN, "Re-challenge your last opponent"));
        duel.challenger.sendSystemMessage(prompt);
        duel.target.sendSystemMessage(prompt);
    }

    /** Update ranked standings, announce rank changes, and track lifetime stats. */
    private static void applyRanked(ServerPlayer winner, ServerPlayer loser) {
        if (winner == null || loser == null) return;
        Leaderboard board = Leaderboard.get(winner.serverLevel().getServer());
        Leaderboard.Entry wBefore = board.entry(winner.getUUID());
        Leaderboard.Entry lBefore = board.entry(loser.getUUID());
        int wOld = wBefore == null ? Leaderboard.START : wBefore.rating();
        int lOld = lBefore == null ? Leaderboard.START : lBefore.rating();

        int[] ratings = board.recordDuel(winner, loser);

        StatsTracker.bump(winner, "ranked_wins");
        StatsTracker.bump(loser, "ranked_losses");
        StatsTracker.recordMax(winner, "ranked_peak", ratings[0]);
        StatsTracker.recordMax(loser, "ranked_peak", ratings[1]);

        announceRank(winner, wOld, ratings[0], board.rankOf(winner.getUUID()), true);
        announceRank(loser, lOld, ratings[1], board.rankOf(loser.getUUID()), false);
    }

    /** Tell a player their new rating and, on a tier/division change, celebrate it. */
    private static void announceRank(ServerPlayer player, int oldRating, int newRating, int rank, boolean won) {
        int delta = newRating - oldRating;
        String sign = delta >= 0 ? "+" : "";
        player.sendSystemMessage(Component.literal(RankTier.label(newRating))
                .withStyle(RankTier.of(newRating).color, ChatFormatting.BOLD)
                .append(Component.literal("  " + newRating + " (" + sign + delta + ")  ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("rank #" + rank).withStyle(ChatFormatting.DARK_GRAY)));

        int before = RankTier.score(oldRating);
        int after = RankTier.score(newRating);
        if (after > before) {
            player.sendSystemMessage(Component.literal(">> PROMOTED to " + RankTier.label(newRating) + "! <<")
                    .withStyle(RankTier.of(newRating).color, ChatFormatting.BOLD));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.2F);
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.5, 0.4, 0.1);
        } else if (after < before) {
            player.sendSystemMessage(Component.literal("Demoted to " + RankTier.label(newRating)
                    + " — win it back!").withStyle(ChatFormatting.RED));
        }
    }

    private static void clear(Duel duel) {
        ACTIVE.remove(duel.challenger.getUUID());
        ACTIVE.remove(duel.target.getUUID());
        duel.turnDeadline = Long.MAX_VALUE;
        for (UUID id : duel.spectators) {
            SPECTATING.remove(id);
        }
        duel.spectators.clear();
    }

    /** Pay out spectator side bets pari-mutuel: winners split the whole pool. */
    private static void settleSideBets(Duel duel, ServerPlayer winner) {
        if (duel.sideBets.isEmpty()) return;
        MinecraftServer server = duel.challenger.getServer();
        UUID winnerId = winner == null ? null : winner.getUUID();
        int pool = 0;
        int winningStake = 0;
        for (SideBet bet : duel.sideBets.values()) {
            pool += bet.amount();
            if (winnerId != null && bet.on().equals(winnerId)) winningStake += bet.amount();
        }
        boolean noMarket = winnerId == null || winningStake == 0;
        for (Map.Entry<UUID, SideBet> e : duel.sideBets.entrySet()) {
            ServerPlayer better = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            SideBet bet = e.getValue();
            if (noMarket) {
                if (better != null) {
                    giveEmeralds(better, bet.amount());
                    better.sendSystemMessage(Component.literal("Side bet refunded (no winning market).")
                            .withStyle(ChatFormatting.GRAY));
                }
            } else if (bet.on().equals(winnerId)) {
                int payout = (int) Math.round((double) bet.amount() / winningStake * pool);
                if (better != null) {
                    giveEmeralds(better, payout);
                    better.sendSystemMessage(Component.literal("Your side bet won ")
                            .withStyle(ChatFormatting.GOLD).append(emeralds(payout))
                            .append(Component.literal("!").withStyle(ChatFormatting.GOLD)));
                }
            } else if (better != null) {
                better.sendSystemMessage(Component.literal("Your side bet lost.")
                        .withStyle(ChatFormatting.RED));
            }
        }
        duel.sideBets.clear();
    }

    private static void sendBoth(Duel duel, Component message) {
        duel.challenger.sendSystemMessage(message);
        duel.target.sendSystemMessage(message);
        sendSpectators(duel, message);
    }

    private static void sendSpectators(Duel duel, Component message) {
        if (duel.spectators.isEmpty()) return;
        MinecraftServer server = duel.challenger.getServer();
        if (server == null) return;
        for (UUID id : duel.spectators) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) sp.sendSystemMessage(message);
        }
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
