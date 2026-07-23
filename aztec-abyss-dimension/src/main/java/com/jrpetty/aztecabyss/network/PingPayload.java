package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: the player pressed the ping key while looking at a spot.
 * Carries the client-computed target block; the server flashes a marker there
 * and calls it out to the squad.
 */
public record PingPayload(int x, int y, int z) implements CustomPacketPayload {

    public static final Type<PingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "abyss_ping"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PingPayload::x,
                    ByteBufCodecs.VAR_INT, PingPayload::y,
                    ByteBufCodecs.VAR_INT, PingPayload::z,
                    PingPayload::new);

    @Override
    public Type<PingPayload> type() {
        return TYPE;
    }
}
