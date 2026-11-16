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
                              int packed, int flags, String place, int score) implements CustomPacketPayload {

    /** A recap from one of the built-in arenas, which are named by ordinal in {@code flags}. */
    public RunRecapPayload(int round, int kills, int headshots, int survivalSeconds, int packed, int flags) {
        this(round, kills, headshots, survivalSeconds, packed, flags, "", -1);
    }

    private static final int FLAG_VICTORY = 1;
    private static final int FLAG_MULTIPLAYER = 2;
    private static final int FLAG_EXTRACTED = 4;
    private static final int FLAG_RITUAL = 8;

    // packed = previousBest | deaths | revives, 10 bits each (0-1023).
    private static final int MASK = 0x3FF;

    public static final Type<RunRecapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "run_recap"));

    /**
     * Written by hand: eight fields is past what {@code StreamCodec.composite}
     * takes. {@code place} names a player's map (empty for a built-in arena)
     * and {@code score} is the points a map's economy left you with, or -1.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RunRecapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.round());
                ByteBufCodecs.VAR_INT.encode(buf, p.kills());
                ByteBufCodecs.VAR_INT.encode(buf, p.headshots());
                ByteBufCodecs.VAR_INT.encode(buf, p.survivalSeconds());
                ByteBufCodecs.VAR_INT.encode(buf, p.packed());
                ByteBufCodecs.VAR_INT.encode(buf, p.flags());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.place());
                ByteBufCodecs.VAR_INT.encode(buf, p.score());
            },
            buf -> new RunRecapPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    /** True for a run on a player's map rather than a built-in arena. */
    public boolean playerMap() {
        return !place.isEmpty();
    }

    /**
     * The four outcome bits, plus the arena the run was on in bits 8-15 -
     * stored as ordinal + 1, so zero still means "not said" and a recap from
     * an older server reads as an unnamed run rather than as the Temple.
     */
    public static int packFlags(boolean victory, boolean multiplayer, boolean extracted, boolean ritual,
                                int mapOrdinal) {
        return (victory ? FLAG_VICTORY : 0)
                | (multiplayer ? FLAG_MULTIPLAYER : 0)
                | (extracted ? FLAG_EXTRACTED : 0)
                | (ritual ? FLAG_RITUAL : 0)
                | (((mapOrdinal + 1) & 0xFF) << 8);
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

    /** The arena's ordinal, or -1 when the server did not say. */
    public int mapOrdinal() {
        return ((flags >> 8) & 0xFF) - 1;
    }

    @Override
    public Type<RunRecapPayload> type() {
        return TYPE;
    }
}
