package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -&gt; client: open the Twenty-One table. */
public record BlackjackMenuPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlackjackMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "blackjack_menu"));

    public static final StreamCodec<ByteBuf, BlackjackMenuPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, p -> 0,
                    ignored -> new BlackjackMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
