package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server: the answer to a duel invite.
 *
 * <p>Carries nothing but yes or no. The server already knows who challenged
 * whom, so a client saying which duel it means would only be something to
 * forge.
 */
public record DuelReplyPayload(boolean accept) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DuelReplyPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "duel_reply"));

    public static final StreamCodec<ByteBuf, DuelReplyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DuelReplyPayload::accept,
                    DuelReplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
