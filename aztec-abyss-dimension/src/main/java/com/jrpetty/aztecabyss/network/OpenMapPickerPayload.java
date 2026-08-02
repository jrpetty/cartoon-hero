package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: pop open the arena picker, pre-selecting whatever the
 * player chose last time. Sent when they right-click a lit Abyss portal.
 */
public record OpenMapPickerPayload(int currentChoice, java.util.List<Integer> bestRounds,
                                   java.util.List<String> customMaps)
        implements CustomPacketPayload {

    public static final Type<OpenMapPickerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "open_map_picker"));

    /**
     * Published maps, sent as text rather than looked up on the client.
     *
     * <p>A published map is a file in the world folder, so the client has no way to
     * know one exists - it is not in a registry, not in a datapack the client
     * receives, and not an enum constant. The server is the only side that can
     * know, so it says. Each entry is {@code name|title|difficulty|blurb}, which is
     * everything a card needs and nothing more.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMapPickerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenMapPickerPayload::currentChoice,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OpenMapPickerPayload::bestRounds,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenMapPickerPayload::customMaps,
                    OpenMapPickerPayload::new);

    /** Field {@code i} of a packed custom-map entry, or empty. */
    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    /** This player's personal best round on the given arena, 0 if never run. */
    public int bestOn(int mapId) {
        return (mapId >= 0 && mapId < bestRounds.size()) ? bestRounds.get(mapId) : 0;
    }

    @Override
    public Type<OpenMapPickerPayload> type() {
        return TYPE;
    }
}
