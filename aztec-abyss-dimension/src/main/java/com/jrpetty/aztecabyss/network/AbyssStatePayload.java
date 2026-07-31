package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client sync of the state the client needs to drive the "Upside
 * Down" atmosphere and the live run HUD: whether the viewer is in an active
 * run, which round it is (so the fog can close in as rounds climb), whether
 * this is a special fog round (pea-soup mist), how many enemies remain, the
 * squad's up/total headcount, and the viewer's own kill tally this run.
 *
 * {@code packed} carries three things in one int - up-count, total-count and the
 * board count on each of up to four horde gates - because the composite codec
 * tops out at six field pairs and this payload is already at all six.
 *
 * <p>Layout, low bits first: total (8) | up (8) | gate boards (4 x 4). A gate
 * nibble of {@code 0xF} means the active map has no barricades at all, which is
 * how the client knows to leave that row off the HUD entirely.
 */
public record AbyssStatePayload(boolean inRun, int round, boolean fogRound,
                                int enemiesRemaining, int packed, int myKills)
        implements CustomPacketPayload {

    public static final Type<AbyssStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "abyss_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AbyssStatePayload::inRun,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::round,
                    ByteBufCodecs.BOOL, AbyssStatePayload::fogRound,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::enemiesRemaining,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::packed,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::myKills,
                    AbyssStatePayload::new);

    /** Nibble value meaning "no barricades on this map". */
    public static final int NO_GATE = 0xF;

    public static int pack(int up, int total, int gateBoardsPacked) {
        return ((gateBoardsPacked & 0xFFFF) << 16) | ((up & 0xFF) << 8) | (total & 0xFF);
    }

    public int playersUp() {
        return (packed >> 8) & 0xFF;
    }

    public int playersTotal() {
        return packed & 0xFF;
    }

    /** Boards left on gate {@code i}, or {@link #NO_GATE} if this map has none. */
    public int gateBoards(int i) {
        return (packed >>> (16 + i * 4)) & 0xF;
    }

    /** Whether the active map has boarded gates worth showing on the HUD. */
    public boolean hasGates() {
        return gateBoards(0) != NO_GATE;
    }

    @Override
    public Type<AbyssStatePayload> type() {
        return TYPE;
    }
}
