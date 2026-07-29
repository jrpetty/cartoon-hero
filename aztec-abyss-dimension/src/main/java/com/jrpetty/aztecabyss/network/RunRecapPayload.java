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
public record RunRecapPayload(int round, int kills, int headshots, int survivalSeconds,
                              int packed, int flags) implements CustomPacketPayload {

    private static final int FLAG_VICTORY = 1;
    private static final int FLAG_MULTIPLAYER = 2;
    private static final int FLAG_EXTRACTED = 4;
    private static final int FLAG_RITUAL = 8;

    // packed = previousBest | deaths | revives, 10 bits each (0-1023).
    private static final int MASK = 0x3FF;

    public static final Type<RunRecapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "run_recap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunRecapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RunRecapPayload::round,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::kills,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::headshots,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::survivalSeconds,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::packed,
                    ByteBufCodecs.VAR_INT, RunRecapPayload::flags,
                    RunRecapPayload::new);

    public static int packFlags(boolean victory, boolean multiplayer, boolean extracted, boolean ritual) {
        return (victory ? FLAG_VICTORY : 0)
                | (multiplayer ? FLAG_MULTIPLAYER : 0)
                | (extracted ? FLAG_EXTRACTED : 0)
                | (ritual ? FLAG_RITUAL : 0);
    }

    /** Squeezes three small counters into one int to stay inside the codec's 6-field ceiling. */
    public static int pack(int previousBest, int deaths, int revives) {
        return ((previousBest & MASK) << 20) | ((deaths & MASK) << 10) | (revives & MASK);
    }

    public int previousBest() {
        return (packed >> 20) & MASK;
    }

    public int deaths() {
        return (packed >> 10) & MASK;
    }

    public int revives() {
        return packed & MASK;
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

    public boolean ritualComplete() {
        return (flags & FLAG_RITUAL) != 0;
    }

    @Override
    public Type<RunRecapPayload> type() {
        return TYPE;
    }
}
