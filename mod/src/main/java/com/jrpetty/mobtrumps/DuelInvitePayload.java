package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -&gt; client: somebody has challenged you to a duel.
 *
 * <p>The duel itself has always been played on screen — both sides get synced
 * cards, a turn clock and the opponent's name. Only the handshake was chat, so
 * being challenged meant reading a line of text and typing a command back. This
 * is that handshake, on screen with the rest of it.
 *
 * @param action  {@value #SHOW} to raise the invite, {@value #CLEAR} to drop it
 * @param from    who is challenging
 * @param terms   what is being played for, in words
 * @param seconds how long is left to answer
 */
public record DuelInvitePayload(int action, String from, String terms, int seconds)
        implements CustomPacketPayload {

    public static final int SHOW = 0;
    public static final int CLEAR = 1;

    public static DuelInvitePayload show(String from, String terms, int seconds) {
        return new DuelInvitePayload(SHOW, from, terms, seconds);
    }

    public static DuelInvitePayload clear() {
        return new DuelInvitePayload(CLEAR, "", "", 0);
    }

    public static final CustomPacketPayload.Type<DuelInvitePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "duel_invite"));

    public static final StreamCodec<ByteBuf, DuelInvitePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelInvitePayload::action,
                    ByteBufCodecs.STRING_UTF8, DuelInvitePayload::from,
                    ByteBufCodecs.STRING_UTF8, DuelInvitePayload::terms,
                    ByteBufCodecs.VAR_INT, DuelInvitePayload::seconds,
                    DuelInvitePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
