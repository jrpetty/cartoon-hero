package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the profile screen opened; send me my playtime/death stats. */
public record ProfileRequestPacket() implements CustomPacketPayload {

    public static final Type<ProfileRequestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "profile_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileRequestPacket> STREAM_CODEC =
        StreamCodec.unit(new ProfileRequestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ProfileRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                VoxeliaNetwork.sendProfile(player);
            }
        });
    }
}
