package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -&gt; client: the state of a duel's wager table, sent to both players
 * every time either of them touches it.
 *
 * <p>Written from the receiving player's point of view — "you" and "they"
 * rather than seat A and seat B — so the screen never has to work out which
 * side of the table it is sitting on.
 *
 * <p>{@code nums} packs the small integers in a fixed order:
 * [price, youAgreed, theyAgreed, yourPurse, secondsLeft, bestOf, cardWager,
 * lastMover, theyCanAfford]. {@code lastMover} is {@link #MOVER_NOBODY},
 * {@link #MOVER_YOU} or {@link #MOVER_THEM}.
 *
 * <p>The opponent's purse is deliberately NOT sent — only whether it covers the
 * price showing. That is the whole of what the other player needs in order to
 * negotiate, and none of what they would need to work out how rich you are.
 *
 * <p>{@code yourCard} and {@code theirCard} are the mob card ids each player is
 * holding when the duel is a card wager — the actual cards on the line, drawn
 * on the table so both players can see what they are haggling over. Empty when
 * no card is staked, or when that player is not holding one right now.
 */
public record DuelWagerPayload(int phase, String opponent, String yourCard, String theirCard,
                               List<Integer> nums)
        implements CustomPacketPayload {

    public static final int OPEN = 0;    // the table is up, keep haggling
    public static final int CLOSED = 1;  // dealt, walked away or timed out

    public static final int MOVER_NOBODY = 0;
    public static final int MOVER_YOU = 1;
    public static final int MOVER_THEM = 2;

    // indices into nums, so the screen and the server cannot disagree
    public static final int PRICE = 0;
    public static final int YOU_AGREED = 1;
    public static final int THEY_AGREED = 2;
    public static final int YOUR_PURSE = 3;
    public static final int SECONDS = 4;
    public static final int BEST_OF = 5;
    public static final int CARD_WAGER = 6;
    public static final int LAST_MOVER = 7;
    public static final int THEY_CAN_AFFORD = 8;

    public static DuelWagerPayload closed() {
        return new DuelWagerPayload(CLOSED, "", "", "", List.of());
    }

    public static final CustomPacketPayload.Type<DuelWagerPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "duel_wager"));

    public static final StreamCodec<ByteBuf, DuelWagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelWagerPayload::phase,
                    ByteBufCodecs.STRING_UTF8, DuelWagerPayload::opponent,
                    ByteBufCodecs.STRING_UTF8, DuelWagerPayload::yourCard,
                    ByteBufCodecs.STRING_UTF8, DuelWagerPayload::theirCard,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DuelWagerPayload::nums,
                    DuelWagerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int num(int index, int fallback) {
        return index >= 0 && index < nums.size() ? nums.get(index) : fallback;
    }
}
