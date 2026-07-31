package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Two-way map picker traffic, sharing one payload type.
 *
 * Server -> client with {@code mapId = -1} opens the picker (sent when a player
 * right-clicks a lit portal in the overworld). Client -> server with a real map
 * id records that player's choice for their next trip through.
 */
public record MapSelectPayload(int mapId) implements CustomPacketPayload {

    /** Sentinel the server sends to pop the picker open on the client. */
    public static final int OPEN = -1;

    /**
     * Sentinel meaning "not an arena at all - send me to the maze". Kept well
     * clear of any real map index so adding arenas can never collide with it.
     */
    public static final int MAZE = 900;

    public static final Type<MapSelectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "map_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapSelectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MapSelectPayload::mapId,
                    MapSelectPayload::new);

    @Override
    public Type<MapSelectPayload> type() {
        return TYPE;
    }
}
