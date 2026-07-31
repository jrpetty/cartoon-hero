package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server: play a hand of Twenty-One.
 *
 * <p>{@code CALL} names a stat by ordinal; {@code DEAL} and {@code STAND} ignore
 * it. Nothing here is trusted: the server holds the shoe and the hand, re-checks
 * that the stat has not already been spent, and turns the card itself. A client
 * that asks for a stat twice is refused rather than obeyed.
 */
public record BlackjackActionPayload(int action, int stat) implements CustomPacketPayload {

    public static final int DEAL = 0;
    public static final int CALL = 1;
    public static final int STAND = 2;
    public static final int LEAVE = 3;

    public static BlackjackActionPayload deal() {
        return new BlackjackActionPayload(DEAL, 0);
    }

    public static BlackjackActionPayload call(int statOrdinal) {
        return new BlackjackActionPayload(CALL, statOrdinal);
    }

    public static BlackjackActionPayload stand() {
        return new BlackjackActionPayload(STAND, 0);
    }

    public static BlackjackActionPayload leave() {
        return new BlackjackActionPayload(LEAVE, 0);
    }

    public static final CustomPacketPayload.Type<BlackjackActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "blackjack_action"));

    public static final StreamCodec<ByteBuf, BlackjackActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BlackjackActionPayload::action,
                    ByteBufCodecs.VAR_INT, BlackjackActionPayload::stat,
                    BlackjackActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
