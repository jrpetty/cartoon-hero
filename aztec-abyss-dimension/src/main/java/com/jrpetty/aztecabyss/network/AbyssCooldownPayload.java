package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client sync of a player's re-entry cooldown deadline (epoch millis).
 * Drives the on-screen "Abyss reopens in ..." countdown the player sees while
 * they wait out a death lockout, wherever they are. A value in the past (or 0)
 * means no active cooldown.
 */
public record AbyssCooldownPayload(long cooldownUntil) implements CustomPacketPayload {

    public static final Type<AbyssCooldownPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "abyss_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssCooldownPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, AbyssCooldownPayload::cooldownUntil,
                    AbyssCooldownPayload::new);

    @Override
    public Type<AbyssCooldownPayload> type() {
        return TYPE;
    }
}
