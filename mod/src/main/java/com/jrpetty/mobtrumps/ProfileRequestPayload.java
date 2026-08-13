package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: send me my profile. */
public record ProfileRequestPayload(int unused) implements CustomPacketPayload {

    public static ProfileRequestPayload get() {
        return new ProfileRequestPayload(0);
    }

    public static final CustomPacketPayload.Type<ProfileRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "profile_request"));

    public static final StreamCodec<ByteBuf, ProfileRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ProfileRequestPayload::unused,
                    ProfileRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
