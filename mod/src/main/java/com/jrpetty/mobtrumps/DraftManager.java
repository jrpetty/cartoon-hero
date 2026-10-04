package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draft mode: both players take turns picking cards from one shared random
 * pool, then duel with the hands they drafted. Pure skill plus a little luck.
 *
 * <p>It starts at a dueling table — sit in Draft, and whoever challenges you
 * drafts against you — and the whole draft is played on both players' draft
 * screens: the pool face up, both sets of picks in view, and a clock on every
 * pick so neither player can stall the other. A pick left to the clock takes
 * the strongest card still in the pool.
 */
public final class DraftManager {

    /** Each player drafts a full hand. */
    static final int PICKS_EACH = Battle.HAND_SIZE;
    /** A few cards more than are taken, so the last pick is still a choice. */
    static final int POOL_SIZE = PICKS_EACH * 2 + 4;
    /** Time on the clock for one pick. */
    static final long PICK_MS = 20_000L;

    private static final Map<UUID, Draft> ACTIVE = new ConcurrentHashMap<>();
    /** Scratch set for the tick, so it allocates nothing: both players map to one draft. */
    private static final Set<Draft> TICK_SEEN = new HashSet<>();

    private static final class Draft {
        final ServerPlayer a; // picks first
        final ServerPlayer b;
        final List<MobCard> pool;
        final List<MobCard> picksA = new ArrayList<>();
        final List<MobCard> picksB = new ArrayList<>();
        boolean aToPick = true;
        long deadline = System.currentTimeMillis() + PICK_MS;

        Draft(ServerPlayer a, ServerPlayer b, List<MobCard> pool) {
            this.a = a;
            this.b = b;
            this.pool = pool;
        }

        ServerPlayer picker() {
            return aToPick ? a : b;
        }

        ServerPlayer other(ServerPlayer p) {
            return p.getUUID().equals(a.getUUID()) ? b : a;
        }

        List<MobCard> picksOf(ServerPlayer p) {
            return p.getUUID().equals(a.getUUID()) ? picksA : picksB;
        }

        boolean done() {
            return picksA.size() >= PICKS_EACH && picksB.size() >= PICKS_EACH;
        }
    }

    private DraftManager() {
    }

    public static boolean isDrafting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /**
     * Start a draft between two players at a dueling table, where sitting down
     * and challenging are both players' consent. Returns false if either is busy.
     */
    public static boolean startDirect(ServerPlayer a, ServerPlayer b) {
        if (a.getUUID().equals(b.getUUID())) {
            return false;
        }
        if (isDrafting(a) || isDrafting(b) || DuelManager.isInDuel(a) || DuelManager.isInDuel(b)) {
            return false;
        }
        List<MobCard> pool = new ArrayList<>(
                MobCards.shuffledDeck(POOL_SIZE, ThreadLocalRandom.current()));
        Draft draft = new Draft(a, b, pool);
        ACTIVE.put(a.getUUID(), draft);
        ACTIVE.put(b.getUUID(), draft);
        BattleCommands.shuffleSound(a);
        BattleCommands.shuffleSound(b);
        sync(draft);
        return true;
    }

    /** A pick or a walk-out from the draft screen. */
    public static void handleAction(ServerPlayer player, int action, int value) {
        Draft draft = ACTIVE.get(player.getUUID());
        if (draft == null) {
            return;
        }
        switch (action) {
            case DraftActionPayload.PICK -> {
                if (!draft.picker().getUUID().equals(player.getUUID())) {
                    return; // not your pick: the screen never offers it, so just ignore it
                }
                for (MobCard card : draft.pool) {
                    if (MobCards.ordinal(card.id()) == value) {
                        take(draft, card);
                        return;
                    }
                }
            }
            case DraftActionPayload.LEAVE -> cancel(draft, player, " left the draft");
            default -> {
            }
        }
    }

    /** Take a card off the clock when a player lets it run out (every server tick). */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<Draft> seen = TICK_SEEN;
        seen.clear();
        List<Draft> due = new ArrayList<>();
        for (Draft draft : ACTIVE.values()) {
            if (seen.add(draft) && now >= draft.deadline && !draft.pool.isEmpty()) {
                due.add(draft);
            }
        }
        for (Draft draft : due) {
            take(draft, strongest(draft.pool));
        }
    }

    public static void handleLogout(ServerPlayer player) {
        Draft draft = ACTIVE.get(player.getUUID());
        if (draft != null) {
            cancel(draft, player, " left");
        }
    }

    private static void take(Draft draft, MobCard card) {
        ServerPlayer picker = draft.picker();
        draft.pool.remove(card);
        draft.picksOf(picker).add(card);
        draft.aToPick = !draft.aToPick;
        draft.deadline = System.currentTimeMillis() + PICK_MS;
        picker.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 1.1F);
        draft.other(picker).playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.4F, 0.9F);
        if (draft.done()) {
            finish(draft);
        } else {
            sync(draft);
        }
    }

    /**
     * The card a pick left to the clock takes: the one with the best single
     * stat, by the odds of that stat beating a random card. A fair stand-in
     * for a choice, and never a gift to the player who let the time run out.
     */
    private static MobCard strongest(List<MobCard> pool) {
        MobCard best = pool.get(0);
        double bestOdds = -1;
        for (MobCard card : pool) {
            Stat stat = card.bestStat();
            double odds = MobCards.winOdds(stat, card.stat(stat));
            if (odds > bestOdds) {
                bestOdds = odds;
                best = card;
            }
        }
        return best;
    }

    private static void cancel(Draft draft, ServerPlayer leaver, String why) {
        ACTIVE.remove(draft.a.getUUID());
        ACTIVE.remove(draft.b.getUUID());
        ServerPlayer other = draft.other(leaver);
        PacketDistributor.sendToPlayer(leaver, DraftSyncPayload.closed());
        PacketDistributor.sendToPlayer(other, DraftSyncPayload.closed());
        // said over the top of the world they are dropped back into
        other.displayClientMessage(Component.literal(name(leaver) + why + " — the draft is off.")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    private static void sync(Draft draft) {
        long left = Math.max(0, draft.deadline - System.currentTimeMillis());
        for (ServerPlayer p : new ServerPlayer[]{draft.a, draft.b}) {
            boolean mine = draft.picker().getUUID().equals(p.getUUID());
            PacketDistributor.sendToPlayer(p, DraftSyncPayload.open(name(draft.other(p)), mine,
                    draft.picksOf(p).size() + 1, PICKS_EACH, (int) ((left + 999) / 1000),
                    ids(draft.pool), ids(draft.picksOf(p)), ids(draft.picksOf(draft.other(p)))));
        }
    }

    private static List<String> ids(List<MobCard> cards) {
        List<String> out = new ArrayList<>(cards.size());
        for (MobCard card : cards) {
            out.add(card.id());
        }
        return out;
    }

    private static void finish(Draft draft) {
        ACTIVE.remove(draft.a.getUUID());
        ACTIVE.remove(draft.b.getUUID());
        // the battle screen replaces the draft screen on the first deal; if
        // the duel cannot start, the draft screens must not be left hanging
        if (!DuelManager.startDraftDuel(draft.a, draft.b, draft.picksA, draft.picksB)) {
            PacketDistributor.sendToPlayer(draft.a, DraftSyncPayload.closed());
            PacketDistributor.sendToPlayer(draft.b, DraftSyncPayload.closed());
        }
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
