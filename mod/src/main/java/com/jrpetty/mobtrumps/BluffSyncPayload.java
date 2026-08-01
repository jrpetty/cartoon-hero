package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -&gt; client: the state of a Bluff table.
 *
 * <p>{@code hand} is <em>only ever the receiving player's own cards</em>. No
 * other seat's hand and no face-down card in the pile is ever put on the wire,
 * because a client that has been told what the pile holds is a client that can
 * see through every bluff in the game. The one exception is {@code reveal},
 * which carries the cards a finished challenge has just turned face up — by
 * then they are public.
 *
 * <p>Everything numeric rides in {@code nums} rather than as its own field:
 * {@link StreamCodec#composite} tops out at six components, and the layout is
 * documented on the constants below so both ends read it the same way.
 *
 * @param phase  see the PHASE_ constants on {@code BluffManager}
 * @param hand   the receiving player's cards, as mob ids
 * @param nums   packed state — see the NUM_ constants
 * @param names  seat names in seat order, yours included
 * @param log    the table's history, newest last
 * @param reveal mob ids turned face up by the last challenge; empty otherwise
 */
public record BluffSyncPayload(int phase, List<String> hand, List<Integer> nums,
                               List<String> names, List<String> log, List<String> reveal)
        implements CustomPacketPayload {

    public static final int NUM_MY_SEAT = 0;
    public static final int NUM_SEATS = 1;
    public static final int NUM_TURN = 2;
    public static final int NUM_PILE = 3;
    public static final int NUM_LAST_SEAT = 4;
    public static final int NUM_LAST_COUNT = 5;
    public static final int NUM_PENDING_OUT = 6;
    public static final int NUM_WINNER = 7;
    public static final int NUM_CLAIM_A = 8;
    public static final int NUM_CLAIM_VALUE = 9;
    public static final int NUM_CLAIM_B = 10;
    public static final int NUM_BURNT = 11;
    public static final int NUM_STAKE = 12;
    public static final int NUM_REVEAL_CHALLENGER = 13;
    public static final int NUM_REVEAL_ACCUSED = 14;
    public static final int NUM_REVEAL_WAS_LYING = 15;
    public static final int NUM_REVEAL_TAKEN = 16;
    public static final int NUM_MOVES = 17;
    /** Hand sizes start here, one per seat, in seat order. */
    public static final int NUM_HAND_SIZES = 18;

    public static final CustomPacketPayload.Type<BluffSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "bluff_sync"));

    public static final StreamCodec<ByteBuf, BluffSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BluffSyncPayload::phase,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BluffSyncPayload::hand,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), BluffSyncPayload::nums,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BluffSyncPayload::names,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BluffSyncPayload::log,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BluffSyncPayload::reveal,
                    BluffSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
