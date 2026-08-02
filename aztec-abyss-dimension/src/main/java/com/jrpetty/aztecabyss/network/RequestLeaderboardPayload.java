package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: the Records button was pressed.
 *
 * <p>Asked for rather than pushed with the picker. Boards grow with every run on
 * every map, and sending all of them to everybody who so much as opens a portal
 * would put a packet nobody asked for on the wire every single time. Almost
 * nobody opens the records; the ones who do can wait a tick.
 */
public record RequestLeaderboardPayload(int unused) implements CustomPacketPayload {

    public static final Type<RequestLeaderboardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AztecAbyssConstants.MOD_ID, "request_leaderboards"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLeaderboardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequestLeaderboardPayload::unused,
                    RequestLeaderboardPayload::new);

    @Override
    public Type<RequestLeaderboardPayload> type() {
        return TYPE;
    }
}
