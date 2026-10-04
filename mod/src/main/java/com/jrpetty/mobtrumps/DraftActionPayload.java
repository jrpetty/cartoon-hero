package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server, from the draft screen. A PICK names the card by its stable
 * ordinal in the set, so the server can find it in the pool without trusting
 * anything else the client says.
 */
public record DraftActionPayload(int action, int value) implements CustomPacketPayload {

    public static final int PICK = 0;
    public static final int LEAVE = 1;

    public static final CustomPacketPayload.Type<DraftActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "draft_action"));

    public static final StreamCodec<ByteBuf, DraftActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DraftActionPayload::action,
                    ByteBufCodecs.VAR_INT, DraftActionPayload::value,
                    DraftActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
