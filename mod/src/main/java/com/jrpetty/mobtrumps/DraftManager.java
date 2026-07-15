package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draft mode: both players alternate picking cards from a shared random pool,
 * then duel with the decks they drafted. Pure skill plus a little luck.
 *
 *   /mobtrumps draft <player>     - invite someone to a draft
 *   /mobtrumps draft accept|decline
 *   /mobtrumps pick <mob_id>      - take a card on your pick (clickable)
 */
public final class DraftManager {

    private static final int PICKS_EACH = 8;
    private static final int POOL_SIZE = PICKS_EACH * 2 + 4; // a few leftovers keep choices real
    private static final long INVITE_TTL_MS = 60_000L;

    private static final Map<UUID, Invite> INVITES = new ConcurrentHashMap<>();
    private static final Map<UUID, Draft> ACTIVE = new ConcurrentHashMap<>();

    private record Invite(UUID from, long expiresAt) {
    }

    private static final class Draft {
        final ServerPlayer a; // picks first
        final ServerPlayer b;
        final List<MobCard> pool;
        final List<MobCard> picksA = new ArrayList<>();
        final List<MobCard> picksB = new ArrayList<>();
        boolean aToPick = true;

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

        boolean done() {
            return picksA.size() >= PICKS_EACH && picksB.size() >= PICKS_EACH;
        }
    }

    private DraftManager() {
    }

    public static boolean isDrafting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static int invite(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            from.sendSystemMessage(err("You can't draft against yourself."));
            return 0;
        }
        if (isDrafting(from) || isDrafting(to) || DuelManager.isInDuel(from) || DuelManager.isInDuel(to)) {
            from.sendSystemMessage(err("Someone is already in a game."));
            return 0;
        }
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), System.currentTimeMillis() + INVITE_TTL_MS));
        from.sendSystemMessage(Component.literal("Draft invite sent to " + name(to) + ".")
                .withStyle(ChatFormatting.GREEN));
        to.sendSystemMessage(Component.literal(name(from) + " invites you to a DRAFT: take turns "
                        + "picking from a shared pool, then duel with what you drafted! ")
                .withStyle(ChatFormatting.GOLD)
                .append(BattleCommands.button("[Accept]", "/mobtrumps draft accept",
                        ChatFormatting.GREEN, "Accept the draft"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps draft decline",
                        ChatFormatting.RED, "Decline the draft")));
        return 1;
    }

    public static int accept(ServerPlayer to) {
        Invite invite = INVITES.remove(to.getUUID());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            to.sendSystemMessage(err("No pending draft invite."));
            return 0;
        }
        ServerPlayer from = to.serverLevel().getServer().getPlayerList().getPlayer(invite.from());
        if (from == null) {
            to.sendSystemMessage(err("The inviter is no longer online."));
            return 0;
        }
        if (isDrafting(from) || isDrafting(to) || DuelManager.isInDuel(from) || DuelManager.isInDuel(to)) {
            to.sendSystemMessage(err("Someone is already in a game."));
            return 0;
        }
        begin(from, to);
        return 1;
    }

    /**
     * Start a draft directly between two mutually-present players, skipping the
     * invite/accept dance — used by the dueling table, where both players have
     * already consented by clicking it. Returns false if either is busy.
     */
    public static boolean startDirect(ServerPlayer a, ServerPlayer b) {
        if (a.getUUID().equals(b.getUUID())) {
            return false;
        }
        if (isDrafting(a) || isDrafting(b) || DuelManager.isInDuel(a) || DuelManager.isInDuel(b)) {
            return false;
        }
        begin(a, b);
        return true;
    }

    private static void begin(ServerPlayer a, ServerPlayer b) {
        List<MobCard> pool = new ArrayList<>(
                MobCards.shuffledDeck(POOL_SIZE, ThreadLocalRandom.current()));
        Draft draft = new Draft(a, b, pool);
        ACTIVE.put(a.getUUID(), draft);
        ACTIVE.put(b.getUUID(), draft);
        Component intro = Component.literal("=== DRAFT: " + name(a) + " vs " + name(b)
                + " — " + PICKS_EACH + " picks each ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        a.sendSystemMessage(intro);
        b.sendSystemMessage(intro);
        promptPick(draft);
    }

    public static int decline(ServerPlayer to) {
        Invite invite = INVITES.remove(to.getUUID());
        if (invite == null) {
            to.sendSystemMessage(err("No pending draft invite."));
            return 0;
        }
        ServerPlayer from = to.serverLevel().getServer().getPlayerList().getPlayer(invite.from());
        if (from != null) {
            from.sendSystemMessage(Component.literal(name(to) + " declined the draft.")
                    .withStyle(ChatFormatting.RED));
        }
        to.sendSystemMessage(Component.literal("Draft declined.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    public static int pick(ServerPlayer player, String mobId) {
        Draft draft = ACTIVE.get(player.getUUID());
        if (draft == null) {
            player.sendSystemMessage(err("You're not in a draft."));
            return 0;
        }
        if (!draft.picker().getUUID().equals(player.getUUID())) {
            player.sendSystemMessage(err("It's " + name(draft.picker()) + "'s pick."));
            return 0;
        }
        MobCard chosen = null;
        for (MobCard c : draft.pool) {
            if (c.id().equalsIgnoreCase(mobId)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            player.sendSystemMessage(err("That card isn't in the pool."));
            return 0;
        }
        draft.pool.remove(chosen);
        (draft.aToPick ? draft.picksA : draft.picksB).add(chosen);
        Component note = Component.literal(name(player) + " drafts ").withStyle(ChatFormatting.GRAY)
                .append(BattleCommands.cardName(chosen));
        draft.a.sendSystemMessage(note);
        draft.b.sendSystemMessage(note);
        draft.aToPick = !draft.aToPick;

        if (draft.done()) {
            finish(draft);
        } else {
            promptPick(draft);
        }
        return 1;
    }

    public static void handleLogout(ServerPlayer player) {
        INVITES.remove(player.getUUID());
        INVITES.entrySet().removeIf(e -> e.getValue().from().equals(player.getUUID()));
        Draft draft = ACTIVE.remove(player.getUUID());
        if (draft != null) {
            ServerPlayer other = draft.other(player);
            ACTIVE.remove(other.getUUID());
            other.sendSystemMessage(Component.literal(name(player) + " left — draft cancelled.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void promptPick(Draft draft) {
        ServerPlayer picker = draft.picker();
        ServerPlayer waiter = draft.other(picker);
        int pickNo = (draft.aToPick ? draft.picksA : draft.picksB).size() + 1;
        MutableComponent list = Component.literal("Pick " + pickNo + "/" + PICKS_EACH
                + " — choose a card:\n").withStyle(ChatFormatting.GRAY);
        for (MobCard c : draft.pool) {
            list.append(BattleCommands.button("[" + c.displayName() + "]",
                    "/mobtrumps pick " + c.id(),
                    MobCardItem.tierColor(c.tier()),
                    c.tier().label() + " — hover the name in chat after drafting for stats"));
            list.append(Component.literal(" "));
        }
        picker.sendSystemMessage(list);
        waiter.sendSystemMessage(Component.literal("Waiting for " + name(picker) + " to pick...")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void finish(Draft draft) {
        ACTIVE.remove(draft.a.getUUID());
        ACTIVE.remove(draft.b.getUUID());
        Component done = Component.literal("Draft complete — the duel begins!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        draft.a.sendSystemMessage(done);
        draft.b.sendSystemMessage(done);
        DuelManager.startDraftDuel(draft.a, draft.b, draft.picksA, draft.picksB);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
