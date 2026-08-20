package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "send me the standings for this skill" ({@code -1} = character). */
public record LeaderboardRequestPacket(int skill) implements CustomPacketPayload {

    public static final Type<LeaderboardRequestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "leaderboard_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaderboardRequestPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LeaderboardRequestPacket::skill,
            LeaderboardRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LeaderboardRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                VoxeliaNetwork.sendLeaderboard(player, packet.skill());
            }
        });
    }
}
