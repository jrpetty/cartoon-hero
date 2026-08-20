package com.voxelia.mmo.network;

import com.mojang.serialization.Codec;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientLeaderboard;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server -> client: one page of standings. {@code rows} carries four ints per
 * entry (rank, level, prestige, 1 if it's you) alongside {@code names}, and
 * {@code meta} is [skillOrdinal, yourRank, yourLevel, playersTracked] — which
 * keeps the whole thing to three stream components.
 */
public record LeaderboardPayload(List<String> names, List<Integer> rows, List<Integer> meta)
        implements CustomPacketPayload {

    public static final Type<LeaderboardPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "leaderboard"));

    private static final StreamCodec<ByteBuf, List<Integer>> INTS =
        ByteBufCodecs.fromCodec(Codec.INT.listOf());
    private static final StreamCodec<ByteBuf, List<String>> STRINGS =
        ByteBufCodecs.fromCodec(Codec.STRING.listOf());

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaderboardPayload> STREAM_CODEC =
        StreamCodec.composite(
            STRINGS, LeaderboardPayload::names,
            INTS, LeaderboardPayload::rows,
            INTS, LeaderboardPayload::meta,
            LeaderboardPayload::new);

    /** Ints per row in {@link #rows}. */
    public static final int STRIDE = 4;
    public static final int M_SKILL = 0;
    public static final int M_YOUR_RANK = 1;
    public static final int M_YOUR_LEVEL = 2;
    public static final int M_TRACKED = 3;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LeaderboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLeaderboard.update(payload));
    }
}
