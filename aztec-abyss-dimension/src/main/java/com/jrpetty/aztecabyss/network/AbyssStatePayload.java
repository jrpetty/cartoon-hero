package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client sync of the minimal state the client needs to drive the
 * "Upside Down" atmosphere: whether the viewer is in an active run and which
 * round it is (so the fog can close in as rounds climb). Sent to each
 * participant whenever their run state changes.
 */
public record AbyssStatePayload(boolean inRun, int round) implements CustomPacketPayload {

    public static final Type<AbyssStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "abyss_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AbyssStatePayload::inRun,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::round,
                    AbyssStatePayload::new);

    @Override
    public Type<AbyssStatePayload> type() {
        return TYPE;
    }
}
