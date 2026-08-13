package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open the Memory table. The board itself follows in a sync. */
public record MemoryMenuPayload(int unused) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MemoryMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "memory_menu"));

    public static final StreamCodec<ByteBuf, MemoryMenuPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, MemoryMenuPayload::unused,
                    MemoryMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
