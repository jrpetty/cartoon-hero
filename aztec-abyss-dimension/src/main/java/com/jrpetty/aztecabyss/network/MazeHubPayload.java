package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The Glade's knowledge, on its way to the hub screen.
 *
 * <p>Three bitsets and a roster. The bitsets are the chart - what anybody has
 * walked, what <em>you</em> have walked, and where a Builder left a mark - sent
 * as raw {@link java.util.BitSet#toByteArray()} bytes because that is already
 * the exact shape the server keeps them in and the exact shape the client
 * needs them back in. Nothing is rendered server-side: which pixels those bits
 * become is entirely the screen's business.
 *
 * <p>Sent on request rather than on a cadence. The chart is a kilobyte and a
 * half and changes at walking pace; streaming it every second to somebody who
 * has not opened the hub would be almost all waste. The state packet carries
 * the two percentages for the HUD, and the full grid travels only when there
 * is a screen to draw it on.
 *
 * <p>Each roster row is {@code name|jobDisplay|level|chartPct} - flat strings,
 * for the same reason the skill sheet uses them: the client cannot look any of
 * this up, so the server says it outright.
 *
 * <p>The waypoints ride along as packed {@link net.minecraft.core.BlockPos}
 * longs - the exact shape the torch ledger keeps them in. Block-precise
 * rather than cell-precise, because a torch is a point you planted, not a
 * corridor you walked, and the chart draws it at the spot.
 */
public record MazeHubPayload(byte[] glade, byte[] mine, byte[] marks,
                             List<String> roster, List<Long> waypoints)
        implements CustomPacketPayload {

    public static final Type<MazeHubPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "maze_hub"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MazeHubPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE_ARRAY, MazeHubPayload::glade,
                    ByteBufCodecs.BYTE_ARRAY, MazeHubPayload::mine,
                    ByteBufCodecs.BYTE_ARRAY, MazeHubPayload::marks,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MazeHubPayload::roster,
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), MazeHubPayload::waypoints,
                    MazeHubPayload::new);

    @Override
    public Type<MazeHubPayload> type() {
        return TYPE;
    }
}
