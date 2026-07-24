package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: show a floating emote bubble on the battle screen.
 * {@code side} is 0 for the viewer's own card, 1 for the opponent's.
 */
public record BattleEmotePayload(int side, String text) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BattleEmotePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "battle_emote"));

    public static final StreamCodec<ByteBuf, BattleEmotePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BattleEmotePayload::side,
                    ByteBufCodecs.STRING_UTF8, BattleEmotePayload::text,
                    BattleEmotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
