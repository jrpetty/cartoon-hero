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
 * {@code packed} carries the squad's up-count and total in one int, because the
 * composite codec tops out at six field pairs and this payload is at all six.
 * Layout, low bits first: total (8) | up (8) | arena (8). The arena is stored as
 * ordinal + 1, so zero means "not said" - the HUD then names no map rather
 * than the wrong one.
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

    public static int pack(int up, int total, int mapOrdinal) {
        return (((mapOrdinal + 1) & 0xFF) << 16) | ((up & 0xFF) << 8) | (total & 0xFF);
    }

    /** The arena the run is on, as an ordinal, or -1 when the server did not say. */
    public int mapOrdinal() {
        return ((packed >> 16) & 0xFF) - 1;
    }

    public int playersUp() {
        return (packed >> 8) & 0xFF;
    }

    public int playersTotal() {
        return packed & 0xFF;
    }

    @Override
    public Type<AbyssStatePayload> type() {
        return TYPE;
    }
}
