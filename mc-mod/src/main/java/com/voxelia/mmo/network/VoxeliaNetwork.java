package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers payloads (mod event bus) and pushes skill/talent state to clients. */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VoxeliaNetwork {
    private VoxeliaNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SkillsSyncPayload.TYPE, SkillsSyncPayload.STREAM_CODEC, SkillsSyncPayload::handle);
        registrar.playToClient(AbilityCooldownPacket.TYPE, AbilityCooldownPacket.STREAM_CODEC, AbilityCooldownPacket::handle);
        registrar.playToClient(TalentsSyncPayload.TYPE, TalentsSyncPayload.STREAM_CODEC, TalentsSyncPayload::handle);
        registrar.playToServer(AbilityPacket.TYPE, AbilityPacket.STREAM_CODEC, AbilityPacket::handle);
        registrar.playToServer(SpendTalentPacket.TYPE, SpendTalentPacket.STREAM_CODEC, SpendTalentPacket::handle);
    }

    public static void syncTo(ServerPlayer player) {
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        PacketDistributor.sendToPlayer(player, new SkillsSyncPayload(skills));
    }

    public static void syncTalents(ServerPlayer player) {
        PlayerTalents talents = player.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        PacketDistributor.sendToPlayer(player, new TalentsSyncPayload(
            talents.ranks(), VoxeliaConfig.talentMaxRank(), VoxeliaConfig.talentLevelsPerPoint()));
    }

    /** Handle a GUI talent purchase from the client. */
    public static void handleSpendTalent(ServerPlayer player, int talentOrdinal) {
        Talent[] talents = Talent.values();
        if (talentOrdinal < 0 || talentOrdinal >= talents.length) return;
        if (TalentLogic.spend(player, talents[talentOrdinal])) {
            SkillEffects.apply(player);
            syncTo(player);
            syncTalents(player);
        }
    }
}
