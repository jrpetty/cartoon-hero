package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The client asking for the hub sheet - fired by the keybind.
 *
 * <p>Carries nothing. Who is asking comes with the packet, and everything the
 * answer contains is decided server-side, which is the only side that knows
 * any of it. An empty record still needs a codec, so the codec is a unit.
 */
public record RequestMazeHubPayload() implements CustomPacketPayload {

    public static final RequestMazeHubPayload INSTANCE = new RequestMazeHubPayload();

    public static final Type<RequestMazeHubPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "maze_hub_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMazeHubPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<RequestMazeHubPayload> type() {
        return TYPE;
    }
}
