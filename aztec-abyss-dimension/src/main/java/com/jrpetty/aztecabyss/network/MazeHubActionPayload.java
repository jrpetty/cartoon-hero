package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: one of the hub's buttons was pressed.
 *
 * <p>The hub used to reach the trade sheet and the order slate by typing
 * {@code /maze skills} and {@code /maze order} into the chat on the player's
 * behalf. That worked until a server restricted commands, at which point two
 * of the hub's three buttons silently did nothing. This asks for the screen
 * directly; the server decides, exactly as the commands did.
 */
public record MazeHubActionPayload(int action) implements CustomPacketPayload {

    /** Open the trade sheet (skills). */
    public static final int SKILLS = 0;
    /** Open the requisition slate (orders). */
    public static final int ORDERS = 1;

    public static final Type<MazeHubActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "maze_hub_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MazeHubActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MazeHubActionPayload::action,
                    MazeHubActionPayload::new);

    @Override
    public Type<MazeHubActionPayload> type() {
        return TYPE;
    }
}
