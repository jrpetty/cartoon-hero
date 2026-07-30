package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> client: campaign progress.
 *
 * @param states mission id -> 1 cleared, 2 cleared flawlessly
 */
public record CampaignSyncPayload(Map<String, Integer> states) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CampaignSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "campaign_sync"));

    public static final StreamCodec<ByteBuf, CampaignSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                            CampaignSyncPayload::states,
                    CampaignSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
