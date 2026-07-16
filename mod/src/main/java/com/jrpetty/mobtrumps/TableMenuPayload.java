package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: open the dueling-table home menu. Carries who (if anyone)
 * is already seated at this table so the menu can offer a challenge.
 * {@code seatedName} is empty when the table is free; {@code selfSeated} is
 * true when the viewer is the one waiting.
 */
public record TableMenuPayload(BlockPos pos, String seatedName, int seatedMode, boolean selfSeated)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TableMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "table_menu"));

    public static final StreamCodec<ByteBuf, TableMenuPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TableMenuPayload::pos,
            ByteBufCodecs.STRING_UTF8, TableMenuPayload::seatedName,
            ByteBufCodecs.VAR_INT, TableMenuPayload::seatedMode,
            ByteBufCodecs.BOOL, TableMenuPayload::selfSeated,
            TableMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
