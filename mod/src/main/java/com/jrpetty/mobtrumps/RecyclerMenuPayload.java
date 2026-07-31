package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open a recycler machine. 0 = shredder, 1 = press. */
public record RecyclerMenuPayload(int mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RecyclerMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "recycler_menu"));

    public static final StreamCodec<ByteBuf, RecyclerMenuPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RecyclerMenuPayload::mode,
                    RecyclerMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
