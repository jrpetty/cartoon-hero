package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: a button on the Creator Console.
 *
 * <p>{@code action} names what to do - {@code check}, {@code test},
 * {@code stop}, {@code save}, {@code meta}, {@code publish}, {@code unpublish},
 * {@code marker}, {@code wand}, {@code leave}, {@code refresh} - and the two
 * arguments carry whatever it needs: a map name, a ruleset, a marker kind, a
 * {@code field=value} pair. The server checks everything again; a console is a
 * nicer way to ask, never a reason to trust the answer.
 */
public record CreatorActionPayload(String action, String a, String b) implements CustomPacketPayload {

    public static final Type<CreatorActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "creator_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreatorActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(32), CreatorActionPayload::action,
                    ByteBufCodecs.stringUtf8(256), CreatorActionPayload::a,
                    ByteBufCodecs.stringUtf8(512), CreatorActionPayload::b,
                    CreatorActionPayload::new);

    @Override
    public Type<CreatorActionPayload> type() {
        return TYPE;
    }
}
