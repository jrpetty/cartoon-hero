package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One trade, described in full, on its way to the sign-up screen.
 *
 * <p>Sent when somebody right-clicks a post on the trade board. Everything the
 * screen shows is in here - the description, who already does the job, what
 * the clicker currently is - because the client knows none of it and should
 * decide none of it. The screen's confirm button answers with a
 * {@link TradeChoicePayload}, and the server does the actual signing on.
 *
 * @param job     the trade's id ("runner", "builder", ...)
 * @param display the trade's coloured display name
 * @param body    description lines, packed with {@code \n}
 * @param takers  who already has the trade, one line, or empty
 * @param current the clicker's current trade display, or empty for none
 */
public record TradeBoardPayload(String job, String display, String body,
                                String takers, String current)
        implements CustomPacketPayload {

    public static final Type<TradeBoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "trade_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeBoardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, TradeBoardPayload::job,
                    ByteBufCodecs.STRING_UTF8, TradeBoardPayload::display,
                    ByteBufCodecs.STRING_UTF8, TradeBoardPayload::body,
                    ByteBufCodecs.STRING_UTF8, TradeBoardPayload::takers,
                    ByteBufCodecs.STRING_UTF8, TradeBoardPayload::current,
                    TradeBoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
