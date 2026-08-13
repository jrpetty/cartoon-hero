package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The escape, on its way to the ceremony screen.
 *
 * <p>{@code stats} is packed {@code name|days|pct|kills|raidsHeld|seconds|game};
 * each hall line is {@code name|days|pct|kills|seconds|game}, newest first.
 * The server owns every number here - the client only draws the party.
 */
public record MazeVictoryPayload(String stats, List<String> hall, int hallTotal)
        implements CustomPacketPayload {

    public static final Type<MazeVictoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "maze_victory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MazeVictoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MazeVictoryPayload::stats,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MazeVictoryPayload::hall,
                    ByteBufCodecs.VAR_INT, MazeVictoryPayload::hallTotal,
                    MazeVictoryPayload::new);

    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    public static int number(String packed, int index) {
        try {
            return Integer.parseInt(field(packed, index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
