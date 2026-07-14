package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A server-wide single-elimination tournament. Anyone opens one with an entry
 * fee, players join by paying it, and the bracket advances as its scheduled
 * duels finish. The champion takes the whole emerald pot.
 *
 *   /mobtrumps tournament open <fee>   - open registration
 *   /mobtrumps tournament join         - pay the fee and enter
 *   /mobtrumps tournament start        - lock entries and start round 1
 *   /mobtrumps tournament status       - bracket state
 */
public final class TournamentManager {

    private static final int MAX_ENTRANTS = 16;

    private enum Phase { NONE, REGISTERING, RUNNING }

    /** One scheduled duel in the current round; winner==null means unresolved. */
    private static final class Match {
        final UUID a, b;
        UUID winner;

        Match(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        boolean involves(UUID id) {
            return a.equals(id) || b.equals(id);
        }

        UUID other(UUID id) {
            return a.equals(id) ? b : a;
        }
    }

    private static Phase phase = Phase.NONE;
    private static int entryFee = 0;
    private static int pot = 0;
    private static UUID host = null;
    /** entrant UUID -> display name (kept even if they go offline). */
    private static final Map<UUID, String> ENTRANTS = new LinkedHashMap<>();
    private static final List<Match> MATCHES = new ArrayList<>();
    private static final List<UUID> ADVANCING = new ArrayList<>();
    private static int roundNumber = 0;

    private TournamentManager() {
    }

    public static synchronized int open(ServerPlayer opener, int fee) {
        if (phase != Phase.NONE) {
            opener.sendSystemMessage(err("A tournament is already "
                    + (phase == Phase.REGISTERING ? "open for entries." : "running.")));
            return 0;
        }
        phase = Phase.REGISTERING;
        entryFee = Math.max(0, fee);
        pot = 0;
        host = opener.getUUID();
        ENTRANTS.clear();
        MATCHES.clear();
        ADVANCING.clear();
        roundNumber = 0;
        broadcast(opener.getServer(), Component.literal("* " + name(opener)
                        + " opened a MOB TRUMPS TOURNAMENT! Entry: "
                        + (entryFee == 0 ? "free" : entryFee + " emeralds")
                        + " - winner takes the pot. ")
                .withStyle(ChatFormatting.GOLD)
                .append(BattleCommands.button("[Join]", "/mobtrumps tournament join",
                        ChatFormatting.GREEN, "Enter the tournament"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Start]", "/mobtrumps tournament start",
                        ChatFormatting.YELLOW, "Host only: lock entries and begin")));
        return 1;
    }

    public static synchronized int join(ServerPlayer player) {
        if (phase != Phase.REGISTERING) {
            player.sendSystemMessage(err("No tournament is open for entries."));
            return 0;
        }
        if (ENTRANTS.containsKey(player.getUUID())) {
            player.sendSystemMessage(err("You're already entered."));
            return 0;
        }
        if (ENTRANTS.size() >= MAX_ENTRANTS) {
            player.sendSystemMessage(err("The bracket is full (" + MAX_ENTRANTS + ")."));
            return 0;
        }
        if (entryFee > 0 && !takeEmeralds(player, entryFee)) {
            player.sendSystemMessage(err("Entry costs " + entryFee + " emeralds."));
            return 0;
        }
        pot += entryFee;
        ENTRANTS.put(player.getUUID(), name(player));
        broadcast(player.getServer(), Component.literal(name(player) + " entered the tournament ("
                        + ENTRANTS.size() + " in, pot " + pot + " emeralds).")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    public static synchronized int start(ServerPlayer starter) {
        if (phase != Phase.REGISTERING) {
            starter.sendSystemMessage(err("No tournament is waiting to start."));
            return 0;
        }
        if (!starter.getUUID().equals(host) && !starter.hasPermissions(2)) {
            starter.sendSystemMessage(err("Only the host (or an op) can start it."));
            return 0;
        }
        if (ENTRANTS.size() < 2) {
            starter.sendSystemMessage(err("Need at least 2 entrants."));
            return 0;
        }
        phase = Phase.RUNNING;
        List<UUID> seeds = new ArrayList<>(ENTRANTS.keySet());
        Collections.shuffle(seeds, ThreadLocalRandom.current());
        roundNumber = 1;
        buildRound(seeds);
        announceRound(starter.getServer());
        return 1;
    }

    public static synchronized int status(ServerPlayer player) {
        switch (phase) {
            case NONE -> player.sendSystemMessage(Component.literal(
                            "No tournament running. Open one: /mobtrumps tournament open <fee>")
                    .withStyle(ChatFormatting.GRAY));
            case REGISTERING -> player.sendSystemMessage(Component.literal("Tournament open - "
                            + ENTRANTS.size() + "/" + MAX_ENTRANTS + " entered, pot " + pot
                            + " emeralds. ").withStyle(ChatFormatting.GOLD)
                    .append(BattleCommands.button("[Join]", "/mobtrumps tournament join",
                            ChatFormatting.GREEN, "Enter the tournament")));
            case RUNNING -> {
                player.sendSystemMessage(Component.literal("Round " + roundNumber + " - pot " + pot
                        + " emeralds:").withStyle(ChatFormatting.GOLD));
                for (Match m : MATCHES) {
                    String line = m.winner == null
                            ? ENTRANTS.get(m.a) + " vs " + ENTRANTS.get(m.b) + " (playing)"
                            : ENTRANTS.get(m.winner) + " beat " + ENTRANTS.get(m.other(m.winner));
                    player.sendSystemMessage(Component.literal("  " + line)
                            .withStyle(m.winner == null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
                }
                if (!ADVANCING.isEmpty()) {
                    StringBuilder next = new StringBuilder("  Through to the next round: ");
                    for (UUID id : ADVANCING) next.append(ENTRANTS.get(id)).append(" ");
                    player.sendSystemMessage(Component.literal(next.toString())
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return 1;
    }

    /** Called from DuelManager whenever any duel ends with a winner. */
    public static synchronized void onDuelResult(ServerPlayer winner, ServerPlayer loser) {
        if (phase != Phase.RUNNING) return;
        UUID w = winner.getUUID(), l = loser.getUUID();
        for (Match m : MATCHES) {
            if (m.winner == null && m.involves(w) && m.involves(l)) {
                m.winner = w;
                ADVANCING.add(w);
                broadcast(winner.getServer(), Component.literal("Tournament: " + ENTRANTS.get(w)
                        + " knocks out " + ENTRANTS.get(l) + "!").withStyle(ChatFormatting.GOLD));
                maybeAdvance(winner.getServer());
                return;
            }
        }
    }

    /** A logged-out entrant is scratched; their unplayed opponent advances. */
    public static synchronized void handleLogout(ServerPlayer player) {
        if (phase != Phase.RUNNING) return;
        UUID id = player.getUUID();
        for (Match m : MATCHES) {
            if (m.winner == null && m.involves(id)) {
                m.winner = m.other(id);
                ADVANCING.add(m.winner);
                broadcast(player.getServer(), Component.literal("Tournament: " + ENTRANTS.get(id)
                                + " left - " + ENTRANTS.get(m.winner) + " advances.")
                        .withStyle(ChatFormatting.YELLOW));
                maybeAdvance(player.getServer());
                return;
            }
        }
    }

    private static void buildRound(List<UUID> seeds) {
        MATCHES.clear();
        ADVANCING.clear();
        for (int i = 0; i + 1 < seeds.size(); i += 2) {
            MATCHES.add(new Match(seeds.get(i), seeds.get(i + 1)));
        }
        if (seeds.size() % 2 == 1) {
            ADVANCING.add(seeds.get(seeds.size() - 1)); // bye
        }
    }

    private static void announceRound(MinecraftServer server) {
        broadcast(server, Component.literal("=== TOURNAMENT ROUND " + roundNumber + " ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Match m : MATCHES) {
            broadcast(server, Component.literal("  " + ENTRANTS.get(m.a) + " vs " + ENTRANTS.get(m.b)
                            + " - duel with /mobtrumps duel <opponent>")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!ADVANCING.isEmpty()) {
            broadcast(server, Component.literal("  " + ENTRANTS.get(ADVANCING.get(0))
                    + " gets a bye this round.").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void maybeAdvance(MinecraftServer server) {
        for (Match m : MATCHES) {
            if (m.winner == null) return; // still playing
        }
        if (ADVANCING.size() == 1) {
            UUID champ = ADVANCING.get(0);
            String champName = ENTRANTS.get(champ);
            broadcast(server, Component.literal("*** " + champName
                            + " WINS THE TOURNAMENT and the pot of " + pot + " emeralds! ***")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            ServerPlayer online = server.getPlayerList().getPlayer(champ);
            if (online != null && pot > 0) {
                CardActions.giveEmeralds(online, pot);
            }
            reset();
            return;
        }
        List<UUID> seeds = new ArrayList<>(ADVANCING);
        roundNumber++;
        buildRound(seeds);
        announceRound(server);
    }

    private static void reset() {
        phase = Phase.NONE;
        entryFee = 0;
        pot = 0;
        host = null;
        ENTRANTS.clear();
        MATCHES.clear();
        ADVANCING.clear();
        roundNumber = 0;
    }

    private static boolean takeEmeralds(ServerPlayer player, int amount) {
        var inv = player.getInventory();
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(net.minecraft.world.item.Items.EMERALD)) {
                have += inv.getItem(i).getCount();
            }
        }
        if (have < amount) return false;
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            var stack = inv.getItem(i);
            if (stack.is(net.minecraft.world.item.Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return true;
    }

    private static void broadcast(MinecraftServer server, Component message) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
