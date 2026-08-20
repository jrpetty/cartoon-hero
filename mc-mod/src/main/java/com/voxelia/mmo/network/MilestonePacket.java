package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.MilestoneToasts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: a level-gated perk just unlocked; show the toast. */
public record MilestonePacket(int skill, int kind, int level) implements CustomPacketPayload {

    public static final Type<MilestonePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "milestone"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MilestonePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MilestonePacket::skill,
            ByteBufCodecs.VAR_INT, MilestonePacket::kind,
            ByteBufCodecs.VAR_INT, MilestonePacket::level,
            MilestonePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MilestonePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MilestoneToasts.trigger(packet.skill(), packet.kind(), packet.level()));
    }
}
