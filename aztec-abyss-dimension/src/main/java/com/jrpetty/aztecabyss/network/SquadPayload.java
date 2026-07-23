package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client sync of a player's squadmates (everyone else in the run),
 * refreshed a couple of times a second so the HUD can show live health bars,
 * downed status and a marker to anyone who's down. An empty list means solo.
 */
public record SquadPayload(List<TeammateInfo> teammates) implements CustomPacketPayload {

    public static final Type<SquadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "abyss_squad"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SquadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    TeammateInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), SquadPayload::teammates,
                    SquadPayload::new);

    @Override
    public Type<SquadPayload> type() {
        return TYPE;
    }
}
