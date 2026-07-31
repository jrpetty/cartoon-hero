package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -&gt; client: the state of a Twenty-One table.
 *
 * <p>Draws are sent as flat lists rather than as objects because a
 * {@code StreamCodec} composite takes at most six fields. Each draw is
 * {@code "statOrdinal|mobId|value|runningTotal"} — the mob id is included so the
 * screen can show the card that was actually turned, which is the whole moment
 * of the game.
 *
 * @param phase   0 player's call, 1 dealer playing out, 2 settled
 * @param result  0 none, 1 player, 2 dealer, 3 push
 * @param nums    playerTotal, dealerTotal, drawsTaken, fragments, stake
 * @param player  the player's draws
 * @param dealer  the dealer's draws, revealed as it plays
 */
public record BlackjackSyncPayload(int phase, int result, List<Integer> nums,
                                   List<String> player, List<String> dealer)
        implements CustomPacketPayload {

    public static final int PHASE_PLAYER = 0;
    public static final int PHASE_DEALER = 1;
    public static final int PHASE_DONE = 2;

    public static final int RESULT_NONE = 0;
    public static final int RESULT_PLAYER = 1;
    public static final int RESULT_DEALER = 2;
    public static final int RESULT_PUSH = 3;

    public static final CustomPacketPayload.Type<BlackjackSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "blackjack_sync"));

    public static final StreamCodec<ByteBuf, BlackjackSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BlackjackSyncPayload::phase,
                    ByteBufCodecs.VAR_INT, BlackjackSyncPayload::result,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), BlackjackSyncPayload::nums,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BlackjackSyncPayload::player,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BlackjackSyncPayload::dealer,
                    BlackjackSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
