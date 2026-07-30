package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: begin a campaign mission. */
public record CampaignActionPayload(int action, int mission) implements CustomPacketPayload {

    public static final int BEGIN = 0;

    public static final CustomPacketPayload.Type<CampaignActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "campaign_action"));

    public static final StreamCodec<ByteBuf, CampaignActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CampaignActionPayload::action,
                    ByteBufCodecs.VAR_INT, CampaignActionPayload::mission,
                    CampaignActionPayload::new);

    public static CampaignActionPayload begin(int mission) {
        return new CampaignActionPayload(BEGIN, mission);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
