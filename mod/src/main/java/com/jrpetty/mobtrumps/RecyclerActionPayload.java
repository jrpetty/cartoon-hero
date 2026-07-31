package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: run a recycler machine.
 *
 * <p>{@code SHRED} uses {@code mobId} and {@code flags} (1 = foil);
 * {@code PRINT} uses {@code flags} as the tier ordinal and {@code stake} as the
 * fragments invested. The server re-derives everything it can rather than
 * trusting these: the stake is re-clamped, the fragments re-counted and the
 * card re-checked against the book.
 */
public record RecyclerActionPayload(int action, String mobId, int flags, int stake)
        implements CustomPacketPayload {

    public static final int SHRED = 0;
    public static final int SHRED_ALL = 1;
    public static final int PRINT = 2;

    public static RecyclerActionPayload shred(String mobId, boolean foil) {
        return new RecyclerActionPayload(SHRED, mobId, foil ? 1 : 0, 0);
    }

    public static RecyclerActionPayload shredAll() {
        return new RecyclerActionPayload(SHRED_ALL, "", 0, 0);
    }

    public static RecyclerActionPayload print(int tierOrdinal, int stake) {
        return new RecyclerActionPayload(PRINT, "", tierOrdinal, stake);
    }

    public static final CustomPacketPayload.Type<RecyclerActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "recycler_action"));

    public static final StreamCodec<ByteBuf, RecyclerActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RecyclerActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, RecyclerActionPayload::mobId,
                    ByteBufCodecs.VAR_INT, RecyclerActionPayload::flags,
                    ByteBufCodecs.VAR_INT, RecyclerActionPayload::stake,
                    RecyclerActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
