package com.voxelia.mmo.network;

import com.mojang.serialization.Codec;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientTalents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/** Server -> client: the player's talent ranks plus the two rules the talent GUI needs. */
public record TalentsSyncPayload(Map<String, Integer> ranks, int maxRank, int levelsPerPoint)
        implements CustomPacketPayload {

    public static final Type<TalentsSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "talents_sync"));

    private static final StreamCodec<ByteBuf, Map<String, Integer>> MAP_CODEC =
        ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.INT));

    public static final StreamCodec<RegistryFriendlyByteBuf, TalentsSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            MAP_CODEC, TalentsSyncPayload::ranks,
            ByteBufCodecs.VAR_INT, TalentsSyncPayload::maxRank,
            ByteBufCodecs.VAR_INT, TalentsSyncPayload::levelsPerPoint,
            TalentsSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TalentsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTalents.update(payload.ranks(), payload.maxRank(), payload.levelsPerPoint()));
    }
}
