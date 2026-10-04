package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client: everything the Creator Console shows.
 *
 * <ul>
 *   <li>{@code selection} - the Map Wand's box as {@code "W × H × D|volume"},
 *       or empty with no box marked out;</li>
 *   <li>{@code status} - the last thing the console did, in a sentence;</li>
 *   <li>{@code problems} - the last check's findings, one line each, already
 *       coloured; empty means "not checked" unless {@code status} says clean;</li>
 *   <li>{@code maps} - saved maps, newest first, as
 *       {@code name|title|version|difficulty|ruleset|blurb|published};</li>
 *   <li>{@code rulesets} - every ruleset as {@code id|title};</li>
 *   <li>{@code flags} - bit 0: a test run is going; bit 1: the viewer is an
 *       operator.</li>
 * </ul>
 */
public record CreatorConsolePayload(String selection, String status, List<String> problems,
                                    List<String> maps, List<String> rulesets, int flags)
        implements CustomPacketPayload {

    public static final int FLAG_TESTING = 1;
    public static final int FLAG_OPERATOR = 2;

    public static final Type<CreatorConsolePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "creator_console"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreatorConsolePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CreatorConsolePayload::selection,
                    ByteBufCodecs.STRING_UTF8, CreatorConsolePayload::status,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CreatorConsolePayload::problems,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CreatorConsolePayload::maps,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CreatorConsolePayload::rulesets,
                    ByteBufCodecs.VAR_INT, CreatorConsolePayload::flags,
                    CreatorConsolePayload::new);

    /** Field {@code index} of a packed row, or empty. */
    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    public boolean testing() {
        return (flags & FLAG_TESTING) != 0;
    }

    @Override
    public Type<CreatorConsolePayload> type() {
        return TYPE;
    }
}
