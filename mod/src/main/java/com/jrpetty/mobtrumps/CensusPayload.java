package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -&gt; client: how many of each card this server has ever printed.
 *
 * <p>One count per mob, in {@code MobCards.ALL} order, so no ids need sending —
 * the client already has the catalogue and the order is fingerprinted at
 * startup.
 *
 * <p><b>Printed, not existing.</b> {@link SerialRegistry} counts cards issued
 * and never counts them back down, and it cannot: a card shredded in the
 * recycler is knowable, but one burned in lava, lost on death or despawned in
 * a corner of the world is not. Reporting a number as "in existence" when it
 * silently ignores every card that has quietly stopped existing would be worse
 * than useless — it would be confidently wrong. So the wording everywhere is
 * "printed", which is exactly what the ledger knows, and is the rarity signal
 * that actually matters: a print run.
 */
public record CensusPayload(List<Integer> printed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CensusPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "census"));

    public static final StreamCodec<ByteBuf, CensusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), CensusPayload::printed,
                    CensusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
