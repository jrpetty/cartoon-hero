package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: an action chosen on the dueling-table menu.
 * {@code arg} is the PvP mode ordinal for SEAT, or the difficulty ordinal for
 * CPU. {@code useDeck} asks to battle with the player's own custom deck
 * instead of a random deal (CPU battles only; server re-validates).
 */
public record TableActionPayload(BlockPos pos, int action, int arg, boolean useDeck)
        implements CustomPacketPayload {

    public static final int SEAT = 0;      // sit down to wait for a challenger (arg = mode)
    public static final int CHALLENGE = 1; // fight whoever is seated, in their mode
    public static final int CPU = 2;       // start a solo battle (arg = difficulty)
    public static final int STAND = 3;     // give up your seat
    public static final int TWENTY_ONE = 4; // open the Twenty-One table

    public static final CustomPacketPayload.Type<TableActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "table_action"));

    public static final StreamCodec<ByteBuf, TableActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TableActionPayload::pos,
            ByteBufCodecs.VAR_INT, TableActionPayload::action,
            ByteBufCodecs.VAR_INT, TableActionPayload::arg,
            ByteBufCodecs.BOOL, TableActionPayload::useDeck,
            TableActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
