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
 * <p>Layout, low bits first: total (8) | up (8) | barricade summary (16). The
 * summary is itself packed - present flag (1) | gates standing open (4) |
 * percent of boards intact (7) - and is aggregate rather than per-gate because
 * the Outpost has ten windows, at which count a per-window gauge is unreadable
 * noise. Which one is going is carried by the callouts and the audio.
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

    public static int pack(int up, int total, int barricadeSummary) {
        return ((barricadeSummary & 0xFFFF) << 16) | ((up & 0xFF) << 8) | (total & 0xFF);
    }

    private int summary() {
        return (packed >>> 16) & 0xFFFF;
    }

    public int playersUp() {
        return (packed >> 8) & 0xFF;
    }

    public int playersTotal() {
        return packed & 0xFF;
    }

    /** Whether the active map has boarded ways in worth showing on the HUD. */
    public boolean hasGates() {
        return (summary() & 1) != 0;
    }

    /** How many ways in are currently standing open. */
    public int gatesOpen() {
        return (summary() >>> 1) & 0xF;
    }

    /** Percentage of all boards still nailed up, 0-100. */
    public int gatesPercent() {
        return (summary() >>> 5) & 0x7F;
    }

    @Override
    public Type<AbyssStatePayload> type() {
        return TYPE;
    }
}
