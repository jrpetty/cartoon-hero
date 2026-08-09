package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's print run, per card.
 *
 * <p>Every card's rarity has so far been a claim: the tier on the face says a
 * Warden is Legendary and a Zombie is Common, and that is true of the drop
 * tables but says nothing about what is actually out there on this server. A
 * server whose players have spent a year in the Nether has a Blaze problem and
 * a Bee shortage, and no tooltip would ever tell them.
 *
 * <p>{@link SerialRegistry} has been counting all along — every issued card
 * takes the next number for its mob — so this is a matter of reading a ledger
 * that already exists, not of tracking anything new.
 */
public final class Census {

    private Census() {
    }

    /** Counts for every card, in catalogue order. */
    public static List<Integer> snapshot(MinecraftServer server) {
        SerialRegistry registry = SerialRegistry.get(server);
        List<MobCard> all = MobCards.ALL;
        List<Integer> out = new ArrayList<>(all.size());
        for (MobCard card : all) {
            out.add(registry.count(card.id()));
        }
        return out;
    }

    public static void send(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new CensusPayload(snapshot(server)));
    }

    /** Send to everyone — used when a print run moves enough to matter. */
    public static void sendAll(MinecraftServer server) {
        CensusPayload payload = new CensusPayload(snapshot(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
