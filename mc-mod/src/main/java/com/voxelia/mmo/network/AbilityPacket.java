package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.progression.Abilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the player pressed an ability keybind. */
public record AbilityPacket(int ability) implements CustomPacketPayload {

    public static final Type<AbilityPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, AbilityPacket::ability, AbilityPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AbilityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Abilities.trigger(player, packet.ability());
            }
        });
    }
}
