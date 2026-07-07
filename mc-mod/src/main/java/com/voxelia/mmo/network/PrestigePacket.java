package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: prestige a skill (by Skill ordinal). */
public record PrestigePacket(int skill) implements CustomPacketPayload {

    public static final Type<PrestigePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "prestige"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrestigePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PrestigePacket::skill,
            PrestigePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PrestigePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                VoxeliaNetwork.handlePrestige(player, packet.skill());
            }
        });
    }
}
