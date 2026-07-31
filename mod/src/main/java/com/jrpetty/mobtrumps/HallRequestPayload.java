package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: send me the Hall of Fame. */
public record HallRequestPayload(int unused) implements CustomPacketPayload {

    public static HallRequestPayload get() {
        return new HallRequestPayload(0);
    }

    public static final CustomPacketPayload.Type<HallRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "hall_request"));

    public static final StreamCodec<ByteBuf, HallRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, HallRequestPayload::unused,
                    HallRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
