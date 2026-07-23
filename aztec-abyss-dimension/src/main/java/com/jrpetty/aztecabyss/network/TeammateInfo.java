package com.jrpetty.aztecabyss.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A single squadmate's state, as shown on a teammate's HUD: display name,
 * health percent (0-100), downed flag, and block position (for the distance /
 * direction marker to anyone who's down).
 */
public record TeammateInfo(String name, int health, boolean downed, int x, int y, int z) {

    public static final StreamCodec<RegistryFriendlyByteBuf, TeammateInfo> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, TeammateInfo::name,
                    ByteBufCodecs.VAR_INT, TeammateInfo::health,
                    ByteBufCodecs.BOOL, TeammateInfo::downed,
                    ByteBufCodecs.VAR_INT, TeammateInfo::x,
                    ByteBufCodecs.VAR_INT, TeammateInfo::y,
                    ByteBufCodecs.VAR_INT, TeammateInfo::z,
                    TeammateInfo::new);
}
