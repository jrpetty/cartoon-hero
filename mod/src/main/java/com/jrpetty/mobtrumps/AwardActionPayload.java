package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server from the book's award pages: collect an achievement reward,
 * or choose the one spawn egg a completed set is worth.
 *
 * @param action {@link #CLAIM} or {@link #CHOOSE_EGG}
 * @param key    the achievement id, or the Category name for an egg choice
 * @param mobId  the chosen mob for {@link #CHOOSE_EGG}, otherwise empty
 */
public record AwardActionPayload(int action, String key, String mobId) implements CustomPacketPayload {

    public static final int CLAIM = 0;
    public static final int CHOOSE_EGG = 1;

    public static final CustomPacketPayload.Type<AwardActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "award_action"));

    public static final StreamCodec<ByteBuf, AwardActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AwardActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, AwardActionPayload::key,
                    ByteBufCodecs.STRING_UTF8, AwardActionPayload::mobId,
                    AwardActionPayload::new);

    public static AwardActionPayload claim(String achievementId) {
        return new AwardActionPayload(CLAIM, achievementId, "");
    }

    public static AwardActionPayload egg(String categoryName, String mobId) {
        return new AwardActionPayload(CHOOSE_EGG, categoryName, mobId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
