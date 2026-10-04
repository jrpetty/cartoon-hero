package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the Map Creator is locked for you - here is the door.
 *
 * <p>The way in for anybody who is not an operator used to be a chat command
 * with the password typed after it, in plain view of the chat log. This opens a
 * screen with a box to type it in instead. {@code failed} is true when the
 * screen is being re-opened after a wrong guess, so it can say so.
 */
public record CreatorGatePayload(boolean failed) implements CustomPacketPayload {

    public static final Type<CreatorGatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "creator_gate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreatorGatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CreatorGatePayload::failed,
                    CreatorGatePayload::new);

    @Override
    public Type<CreatorGatePayload> type() {
        return TYPE;
    }
}
