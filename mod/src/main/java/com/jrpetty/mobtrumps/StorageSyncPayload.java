package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> client: which cards are physically stored inside the collection book. */
public record StorageSyncPayload(List<String> stored, List<String> storedFoil)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StorageSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "storage_sync"));

    public static final StreamCodec<ByteBuf, StorageSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StorageSyncPayload::stored,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StorageSyncPayload::storedFoil,
            StorageSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
