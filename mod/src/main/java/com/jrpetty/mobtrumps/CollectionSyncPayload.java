package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> client: the full set of card ids the player has collected. */
public record CollectionSyncPayload(List<String> collected) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "collection_sync"));

    public static final StreamCodec<ByteBuf, CollectionSyncPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                    .map(CollectionSyncPayload::new, CollectionSyncPayload::collected);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
