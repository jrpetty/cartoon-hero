package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: choose which variant of a mob sits on top of the pile. */
public record SetDisplayPayload(String mobId, boolean foil) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDisplayPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "set_display"));

    public static final StreamCodec<ByteBuf, SetDisplayPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetDisplayPayload::mobId,
            ByteBufCodecs.BOOL, SetDisplayPayload::foil,
            SetDisplayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
