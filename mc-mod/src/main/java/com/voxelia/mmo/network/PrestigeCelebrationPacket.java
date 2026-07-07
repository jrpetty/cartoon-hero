package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.PrestigeCelebration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: a skill was just prestiged; play the celebration flourish. */
public record PrestigeCelebrationPacket(int skill, int prestigeCount) implements CustomPacketPayload {

    public static final Type<PrestigeCelebrationPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "prestige_celebrate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrestigeCelebrationPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PrestigeCelebrationPacket::skill,
            ByteBufCodecs.VAR_INT, PrestigeCelebrationPacket::prestigeCount,
            PrestigeCelebrationPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PrestigeCelebrationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> PrestigeCelebration.trigger(packet.skill(), packet.prestigeCount()));
    }
}
