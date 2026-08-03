package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A point being spent, or a trade being unlearnt.
 *
 * <p>The client never decides anything here - it names a skill and the server
 * checks the trade, the rank ceiling and the balance exactly as the command
 * always did. A screen is a nicer way to ask a question, not a reason to trust
 * the answer.
 */
public record SkillLearnPayload(String skillId) implements CustomPacketPayload {

    /** Sentinel meaning "put every point in this trade back". */
    public static final String FORGET = "";

    public static final Type<SkillLearnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "skill_learn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillLearnPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SkillLearnPayload::skillId,
                    SkillLearnPayload::new);

    @Override
    public Type<SkillLearnPayload> type() {
        return TYPE;
    }
}
