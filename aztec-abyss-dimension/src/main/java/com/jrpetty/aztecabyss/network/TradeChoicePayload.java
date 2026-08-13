package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "I'll take it": the confirm button on the trade sign-up screen.
 *
 * <p>Carries only the trade id. The server re-validates it against the real
 * trade list and does the signing on itself, exactly as the old command did -
 * a client cannot invent a trade, and cannot skip whatever the assignment
 * rules are, because the client only ever expressed a wish.
 */
public record TradeChoicePayload(String job) implements CustomPacketPayload {

    public static final Type<TradeChoicePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "trade_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeChoicePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, TradeChoicePayload::job,
                    TradeChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
