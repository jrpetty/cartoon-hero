package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> client: the results of a pack opening, to drive the reveal screen. */
public record PackOpenedPayload(List<Pull> pulls) implements CustomPacketPayload {

    /** One pulled card: which mob, whether it is a foil, and whether it is new. */
    public record Pull(String mobId, boolean foil, boolean isNew) {
        public static final StreamCodec<ByteBuf, Pull> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Pull::mobId,
                ByteBufCodecs.BOOL, Pull::foil,
                ByteBufCodecs.BOOL, Pull::isNew,
                Pull::new);
    }

    public static final CustomPacketPayload.Type<PackOpenedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "pack_opened"));

    public static final StreamCodec<ByteBuf, PackOpenedPayload> STREAM_CODEC =
            Pull.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(PackOpenedPayload::new, PackOpenedPayload::pulls);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
