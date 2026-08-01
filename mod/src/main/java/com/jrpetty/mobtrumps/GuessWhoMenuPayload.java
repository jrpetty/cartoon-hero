package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -&gt; client: open the Guess Who board. */
public record GuessWhoMenuPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GuessWhoMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "guesswho_menu"));

    public static final StreamCodec<ByteBuf, GuessWhoMenuPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, p -> 0,
                    ignored -> new GuessWhoMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
