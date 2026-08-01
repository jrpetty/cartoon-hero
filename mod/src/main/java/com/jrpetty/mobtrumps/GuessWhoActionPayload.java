package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server: play Guess Who.
 *
 * <p>{@code ASK} names a question by its index in the catalogue plus a value for
 * the threshold ones; {@code GUESS} names a mob. Nothing is trusted: the server
 * re-clamps the value, refuses a question that would eliminate nothing, and
 * refuses a guess at a face already struck off. The client never says what
 * should be eliminated — it only asks, and is told what is left.
 */
public record GuessWhoActionPayload(int action, int template, int value, String mobId)
        implements CustomPacketPayload {

    public static final int NEW_GAME = 0;
    public static final int ASK = 1;
    public static final int GUESS = 2;
    public static final int LEAVE = 3;

    public static GuessWhoActionPayload newGame() {
        return new GuessWhoActionPayload(NEW_GAME, 0, 0, "");
    }

    public static GuessWhoActionPayload ask(int template, int value) {
        return new GuessWhoActionPayload(ASK, template, value, "");
    }

    public static GuessWhoActionPayload guess(String mobId) {
        return new GuessWhoActionPayload(GUESS, 0, 0, mobId);
    }

    public static GuessWhoActionPayload leave() {
        return new GuessWhoActionPayload(LEAVE, 0, 0, "");
    }

    public static final CustomPacketPayload.Type<GuessWhoActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "guesswho_action"));

    public static final StreamCodec<ByteBuf, GuessWhoActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GuessWhoActionPayload::action,
                    ByteBufCodecs.VAR_INT, GuessWhoActionPayload::template,
                    ByteBufCodecs.VAR_INT, GuessWhoActionPayload::value,
                    ByteBufCodecs.STRING_UTF8, GuessWhoActionPayload::mobId,
                    GuessWhoActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
