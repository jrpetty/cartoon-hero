package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: what a machine just did, so the screen can play it out.
 *
 * <p>The chat line is the receipt; this is the event. A press that has just
 * eaten 180 fragments owes you more than a grey line of text.
 *
 * @param kind    what happened, see the constants
 * @param mobId   the card shredded or printed, empty where there isn't one
 * @param amount  fragments gained or lost
 * @param count   cards involved, for a bulk shred
 */
public record RecyclerResultPayload(int kind, String mobId, int amount, int count)
        implements CustomPacketPayload {

    public static final int SHRED = 0;
    public static final int SHRED_ALL = 1;
    public static final int PRINT_HIT = 2;
    public static final int PRINT_MISS = 3;
    public static final int REFUSED = 4;

    public static final CustomPacketPayload.Type<RecyclerResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "recycler_result"));

    public static final StreamCodec<ByteBuf, RecyclerResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RecyclerResultPayload::kind,
                    ByteBufCodecs.STRING_UTF8, RecyclerResultPayload::mobId,
                    ByteBufCodecs.VAR_INT, RecyclerResultPayload::amount,
                    ByteBufCodecs.VAR_INT, RecyclerResultPayload::count,
                    RecyclerResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
