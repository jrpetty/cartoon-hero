package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: spend one talent point (global Talent ordinal). */
public record SpendTalentPacket(int talent) implements CustomPacketPayload {

    public static final Type<SpendTalentPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "spend_talent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpendTalentPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpendTalentPacket::talent,
            SpendTalentPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpendTalentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                VoxeliaNetwork.handleSpendTalent(player, packet.talent());
            }
        });
    }
}
