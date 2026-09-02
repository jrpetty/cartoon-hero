package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The induction: every trade at once, for somebody who has just come up.
 *
 * <p>The trade board's sheet ({@link TradeBoardPayload}) describes one post,
 * because you reached it by clicking that post. A Greenie standing in the Box
 * has clicked nothing and may not move until they choose, so the thing that
 * opens in front of them has to carry all four - it is the whole decision on
 * one screen, not a pointer to where the decision is made.
 *
 * <p>Each card is {@code job|display|blurb|description|takers|perk} - flat
 * strings, for the same reason every sheet in this mod uses them: the client
 * knows none of it and should decide none of it. The screen answers with the
 * same {@link TradeChoicePayload} the board's confirm sends, so there is one
 * way to sign on however you reached it.
 *
 * <p>Re-sent by the server every few seconds while the player has not chosen.
 * The client treats a repeat as a refresh of the roster lines rather than a
 * fresh screen, so a player mid-read is never yanked back to the top.
 */
public record MazeInductionPayload(List<String> cards) implements CustomPacketPayload {

    public static final Type<MazeInductionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "maze_induction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MazeInductionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MazeInductionPayload::cards,
                    MazeInductionPayload::new);

    /** Field {@code i} of a packed card, or empty. */
    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    @Override
    public Type<MazeInductionPayload> type() {
        return TYPE;
    }
}
