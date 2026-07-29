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
public record OpenMapPickerPayload(int currentChoice) implements CustomPacketPayload {

    public static final Type<OpenMapPickerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "open_map_picker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMapPickerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenMapPickerPayload::currentChoice,
                    OpenMapPickerPayload::new);

    @Override
    public Type<OpenMapPickerPayload> type() {
        return TYPE;
    }
}
