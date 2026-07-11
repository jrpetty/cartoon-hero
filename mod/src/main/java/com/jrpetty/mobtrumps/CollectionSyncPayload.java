package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> client: the card ids the player has collected, plus foils. */
public record CollectionSyncPayload(List<String> collected, List<String> foils)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "collection_sync"));

    public static final StreamCodec<ByteBuf, CollectionSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CollectionSyncPayload::collected,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CollectionSyncPayload::foils,
                    CollectionSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
