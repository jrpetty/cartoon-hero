package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: vanilla stats the profile screen can't derive locally (playtime, deaths, mob kills). */
public record ProfileStatsPayload(int playTimeTicks, int deaths, int mobKills) implements CustomPacketPayload {

    public static final Type<ProfileStatsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "profile_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileStatsPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ProfileStatsPayload::playTimeTicks,
            ByteBufCodecs.VAR_INT, ProfileStatsPayload::deaths,
            ByteBufCodecs.VAR_INT, ProfileStatsPayload::mobKills,
            ProfileStatsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ProfileStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientProfile.update(payload.playTimeTicks(), payload.deaths(), payload.mobKills()));
    }
}
