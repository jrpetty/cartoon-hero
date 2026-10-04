package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: a guess at the Map Creator's password, from the gate screen. */
public record CreatorUnlockPayload(String attempt) implements CustomPacketPayload {

    public static final Type<CreatorUnlockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "creator_unlock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreatorUnlockPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(128), CreatorUnlockPayload::attempt,
                    CreatorUnlockPayload::new);

    @Override
    public Type<CreatorUnlockPayload> type() {
        return TYPE;
    }
}
