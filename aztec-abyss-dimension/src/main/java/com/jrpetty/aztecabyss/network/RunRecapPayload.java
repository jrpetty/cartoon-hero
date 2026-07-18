package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the end-of-run summary that opens the death/victory recap
 * screen. Sent to a player the moment their run resolves.
 *
 * (victory + multiplayer are packed into a single {@code flags} int so the
 * payload stays within {@link StreamCodec#composite}'s 6-field limit.)
 */
public record RunRecapPayload(int round, int kills, int revives, int survivalSeconds,
                              int previousBest, int flags) implements CustomPacketPayload {

    private static final int FLAG_VICTORY = 1;
    private static final int FLAG_MULTIPLAYER = 2;
    private static final int FLAG_EXTRACTED = 4;

    public static final Type<RunRecapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "run_recap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunRecapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RunRecapPayload::round,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::kills,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::revives,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::survivalSeconds,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::previousBest,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::flags,
                    RunRecapPayload::new);

    public static int packFlags(boolean victory, boolean multiplayer, boolean extracted) {
        return (victory ? FLAG_VICTORY : 0) | (multiplayer ? FLAG_MULTIPLAYER : 0) | (extracted ? FLAG_EXTRACTED : 0);
    }

    public boolean victory() {
        return (flags & FLAG_VICTORY) != 0;
    }

    public boolean multiplayer() {
        return (flags & FLAG_MULTIPLAYER) != 0;
    }

    public boolean extracted() {
        return (flags & FLAG_EXTRACTED) != 0;
    }

    @Override
    public Type<RunRecapPayload> type() {
        return TYPE;
    }
}
