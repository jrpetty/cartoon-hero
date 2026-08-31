package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Records, on their way to the screen that shows them.
 *
 * <p>Sent as flat strings rather than a structured record list, which is the same
 * call the map picker makes and for the same reason: the client cannot look any of
 * this up. Boards are keyed by map name, and a published map is a file in the
 * world folder that no client has ever heard of - there is no registry to consult
 * and no datapack carrying it. The server knows, so the server says, and the
 * client's only job is to draw it.
 *
 * <p>Each row is {@code board|place|name|score|seconds|party}, where {@code board}
 * is the map key with {@code #solo} or {@code #group} on the end. One flat list
 * covers every map and both boards, so opening the screen is one packet however
 * many maps a server has.
 *
 * <p>{@code runs} carries the asking player's own history, newest first, packed
 * as {@code map|outcome|score|seconds|kills|revives|charted|party|when}. It rides
 * along in the same packet because it is wanted at the same moment: somebody
 * opening Records at the portal is asking both "what is the record" and "how did
 * I do last time", and two packets to answer one press is a screen that arrives
 * in pieces.
 */
public record LeaderboardPayload(List<String> rows, List<String> labels, List<String> runs)
        implements CustomPacketPayload {

    public static final Type<LeaderboardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "leaderboards"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaderboardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LeaderboardPayload::rows,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LeaderboardPayload::labels,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LeaderboardPayload::runs,
                    LeaderboardPayload::new);

    /** Field {@code index} of a packed row, or empty. */
    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    @Override
    public Type<LeaderboardPayload> type() {
        return TYPE;
    }
}
