package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server: what a player just did at the wager table.
 *
 * <p>{@code value} is the price for {@link #PROPOSE} and ignored otherwise. It
 * is a request, not an instruction — the server clamps it, decides whether the
 * player is even at a table, and works out which seat they are in. Nothing here
 * names the other player or the duel, so there is nothing here to forge.
 */
public record DuelWagerActionPayload(int action, int value) implements CustomPacketPayload {

    public static final int PROPOSE = 0;  // name a price
    public static final int AGREE = 1;    // accept the price showing
    public static final int REOPEN = 2;   // take the agreements back off
    public static final int LEAVE = 3;    // walk away from the whole thing

    public static final CustomPacketPayload.Type<DuelWagerActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "duel_wager_action"));

    public static final StreamCodec<ByteBuf, DuelWagerActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelWagerActionPayload::action,
                    ByteBufCodecs.VAR_INT, DuelWagerActionPayload::value,
                    DuelWagerActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
