package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -&gt; client: open the Bluff table. */
public record BluffMenuPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BluffMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "bluff_menu"));

    public static final StreamCodec<ByteBuf, BluffMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new BluffMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
