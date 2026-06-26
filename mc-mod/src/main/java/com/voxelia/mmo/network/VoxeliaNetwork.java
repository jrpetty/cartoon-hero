package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers payloads (mod event bus) and pushes skill state to clients. */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VoxeliaNetwork {
    private VoxeliaNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
            SkillsSyncPayload.TYPE,
            SkillsSyncPayload.STREAM_CODEC,
            SkillsSyncPayload::handle);
        registrar.playToServer(
            AbilityPacket.TYPE,
            AbilityPacket.STREAM_CODEC,
            AbilityPacket::handle);
    }

    public static void syncTo(ServerPlayer player) {
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        PacketDistributor.sendToPlayer(player, new SkillsSyncPayload(skills));
    }
}
