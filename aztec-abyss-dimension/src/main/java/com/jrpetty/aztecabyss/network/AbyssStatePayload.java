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
 * {@code playersPacked} packs up-count and total-count into one int to keep the
 * composite codec within its 6-pair ceiling.
 */
public record AbyssStatePayload(boolean inRun, int round, boolean fogRound,
                                int enemiesRemaining, int playersPacked, int myKills)
        implements CustomPacketPayload {

    public static final Type<AbyssStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "abyss_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AbyssStatePayload::inRun,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::round,
                    ByteBufCodecs.BOOL, AbyssStatePayload::fogRound,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::enemiesRemaining,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::playersPacked,
                    ByteBufCodecs.VAR_INT, AbyssStatePayload::myKills,
                    AbyssStatePayload::new);

    public static int packPlayers(int up, int total) {
        return (up << 8) | (total & 0xFF);
    }

    public int playersUp() {
        return playersPacked >> 8;
    }

    public int playersTotal() {
        return playersPacked & 0xFF;
    }

    @Override
    public Type<AbyssStatePayload> type() {
        return TYPE;
    }
}
