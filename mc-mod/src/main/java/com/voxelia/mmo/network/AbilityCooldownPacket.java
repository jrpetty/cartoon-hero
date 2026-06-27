package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientAbilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: an ability went on cooldown (for the HUD indicator). */
public record AbilityCooldownPacket(int ordinal, int durationTicks) implements CustomPacketPayload {

    public static final Type<AbilityCooldownPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "ability_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityCooldownPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityCooldownPacket::ordinal,
            ByteBufCodecs.VAR_INT, AbilityCooldownPacket::durationTicks,
            AbilityCooldownPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AbilityCooldownPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAbilities.setCooldown(packet.ordinal(), packet.durationTicks()));
    }
}
