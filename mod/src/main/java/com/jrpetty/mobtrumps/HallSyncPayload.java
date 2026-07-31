package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client: the Hall of Fame, flattened for the screen.
 *
 * <p>Sent only when the player asks to see it. These are server-wide records
 * that change rarely, so there is nothing to gain from pushing them and
 * everything to lose from pushing them to everyone on every discovery.
 *
 * @param firstPrints "mobId|player" for every 000001 issued
 * @param categories  "category|player" for each set completed first
 * @param completions player names who have finished all 81, in order
 */
public record HallSyncPayload(List<String> firstPrints, List<String> categories,
                              List<String> completions) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HallSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "hall_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRINGS =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, HallSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    STRINGS, HallSyncPayload::firstPrints,
                    STRINGS, HallSyncPayload::categories,
                    STRINGS, HallSyncPayload::completions,
                    HallSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
