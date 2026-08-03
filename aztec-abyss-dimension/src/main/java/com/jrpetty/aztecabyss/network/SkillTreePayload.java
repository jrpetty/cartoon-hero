package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A player's trade sheet, on its way to the screen that draws it.
 *
 * <p>Flat strings for the same reason the leaderboard uses them: the client has
 * no way to look any of this up. Skills live in a server class, their rank text
 * is written there, and the ranks somebody has bought are in a SavedData no
 * client has ever seen. The server knows, so the server says.
 *
 * <p>Each row is {@code id|display|rank|maxRank|rank1|rank2|rank3}. One packet
 * carries the whole sheet, so opening the screen never waits on a second round
 * trip and re-opening it after spending a point is a single message.
 */
public record SkillTreePayload(String job, String display, int available,
                               int have, int need, List<String> skills)
        implements CustomPacketPayload {

    public static final Type<SkillTreePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "skill_tree"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillTreePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SkillTreePayload::job,
                    ByteBufCodecs.STRING_UTF8, SkillTreePayload::display,
                    ByteBufCodecs.VAR_INT, SkillTreePayload::available,
                    ByteBufCodecs.VAR_INT, SkillTreePayload::have,
                    ByteBufCodecs.VAR_INT, SkillTreePayload::need,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SkillTreePayload::skills,
                    SkillTreePayload::new);

    /** Field {@code index} of a packed row, or empty. */
    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    public static int number(String packed, int index) {
        try {
            return Integer.parseInt(field(packed, index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Type<SkillTreePayload> type() {
        return TYPE;
    }
}
