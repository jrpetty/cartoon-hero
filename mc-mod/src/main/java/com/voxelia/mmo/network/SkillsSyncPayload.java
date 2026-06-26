package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientSkillData;
import com.voxelia.mmo.skill.PlayerSkills;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the player's full skill state, encoded via PlayerSkills.CODEC. */
public record SkillsSyncPayload(PlayerSkills skills) implements CustomPacketPayload {

    public static final Type<SkillsSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "skills_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillsSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.fromCodec(PlayerSkills.CODEC),
            SkillsSyncPayload::skills,
            SkillsSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Runs on the client; hop to the main thread before touching client state. */
    public static void handle(SkillsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSkillData.update(payload.skills()));
    }
}
