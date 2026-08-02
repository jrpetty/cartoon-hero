package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client -&gt; server: play Mob Bluff.
 *
 * <p>A play names cards by their index in the player's own hand. Nothing is
 * trusted: the server holds the only copy of that hand and refuses an index it
 * does not recognise, a repeat, more than the limit, or a move out of turn. The
 * client never says whether a play is honest — it does not know, and the whole
 * game rests on it not being the one to decide.
 */
public record BluffActionPayload(int action, List<Integer> picks, int stake)
        implements CustomPacketPayload {

    public static final int NEW_GAME = 0;
    public static final int PLAY = 1;
    public static final int CHALLENGE = 2;
    public static final int PASS = 3;
    public static final int LEAVE = 4;
    public static final int SET_SEATS = 5;
    public static final int SET_STAKE = 6;
    /** Give the hand up and eat the wager. */
    public static final int FORFEIT = 7;

    public static BluffActionPayload newGame() {
        return new BluffActionPayload(NEW_GAME, List.of(), 0);
    }

    public static BluffActionPayload play(List<Integer> picks) {
        return new BluffActionPayload(PLAY, List.copyOf(picks), 0);
    }

    public static BluffActionPayload challenge() {
        return new BluffActionPayload(CHALLENGE, List.of(), 0);
    }

    public static BluffActionPayload pass() {
        return new BluffActionPayload(PASS, List.of(), 0);
    }

    public static BluffActionPayload leave() {
        return new BluffActionPayload(LEAVE, List.of(), 0);
    }

    public static BluffActionPayload seats(int n) {
        return new BluffActionPayload(SET_SEATS, List.of(), n);
    }

    public static BluffActionPayload forfeit() {
        return new BluffActionPayload(FORFEIT, List.of(), 0);
    }

    public static BluffActionPayload stake(int index) {
        return new BluffActionPayload(SET_STAKE, List.of(), index);
    }

    public static final CustomPacketPayload.Type<BluffActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "bluff_action"));

    public static final StreamCodec<ByteBuf, BluffActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BluffActionPayload::action,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), BluffActionPayload::picks,
                    ByteBufCodecs.VAR_INT, BluffActionPayload::stake,
                    BluffActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
