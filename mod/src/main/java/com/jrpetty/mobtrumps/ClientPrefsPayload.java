package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client -> server: the handful of book settings the SERVER has to know about,
 * because it is the server that decides whether to send the message at all.
 *
 * <p>Most settings are pure presentation and never leave the client. The hunt
 * counter is the exception: the "Creeper holo: 12 / 100 kills" line is an
 * action-bar message pushed from {@link MobDrops}, so switching it off has to
 * stop it being sent rather than hide it after the fact.
 *
 * @param flags bit 0 = show the hunt counter on kills
 */
public record ClientPrefsPayload(int flags) implements CustomPacketPayload {

    public static final int HUNT_COUNTER = 1;

    public static final CustomPacketPayload.Type<ClientPrefsPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "client_prefs"));

    public static final StreamCodec<ByteBuf, ClientPrefsPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ClientPrefsPayload::flags,
                    ClientPrefsPayload::new);

    /** Per-player flags, rebuilt whenever a client announces itself. */
    private static final Map<UUID, Integer> BY_PLAYER = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void store(ServerPlayer player, int flags) {
        BY_PLAYER.put(player.getUUID(), flags);
    }

    public static void forget(UUID player) {
        BY_PLAYER.remove(player);
    }

    /**
     * Whether this player wants the on-screen hunt counter. Defaults to true so
     * a vanilla-ish client, or one that hasn't announced yet, still sees it.
     */
    public static boolean huntCounter(ServerPlayer player) {
        Integer flags = BY_PLAYER.get(player.getUUID());
        return flags == null || (flags & HUNT_COUNTER) != 0;
    }
}
