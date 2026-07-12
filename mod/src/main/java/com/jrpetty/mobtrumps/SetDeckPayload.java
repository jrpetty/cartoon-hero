package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Client -> server: save the player's chosen battle deck (mob ids). */
public record SetDeckPayload(List<String> deck) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDeckPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "set_deck"));

    public static final StreamCodec<ByteBuf, SetDeckPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                    .map(SetDeckPayload::new, SetDeckPayload::deck);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
