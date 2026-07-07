package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.PrestigeLogic;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerPrestige;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        registrar.playToServer(PrestigePacket.TYPE, PrestigePacket.STREAM_CODEC, PrestigePacket::handle);
    }

    public static void syncTo(ServerPlayer player) {
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        PacketDistributor.sendToPlayer(player, new SkillsSyncPayload(skills));
    }

    public static void syncTalents(ServerPlayer player) {
        PlayerTalents talents = player.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        PlayerPrestige prestige = player.getData(VoxeliaAttachments.PLAYER_PRESTIGE.get());
        PacketDistributor.sendToPlayer(player, new TalentsSyncPayload(
            talents.ranks(), VoxeliaConfig.talentMaxRank(), VoxeliaConfig.talentLevelsPerPoint(),
            prestige.counts(), VoxeliaConfig.prestigePointsPerPrestige(), VoxeliaConfig.prestigeMax()));
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

    /** Handle a prestige request from the client (button/command). */
    public static void handlePrestige(ServerPlayer player, int skillOrdinal) {
        Skill[] skills = Skill.values();
        if (skillOrdinal < 0 || skillOrdinal >= skills.length) return;
        Skill skill = skills[skillOrdinal];
        if (PrestigeLogic.prestige(player, skill)) {
            SkillEffects.apply(player);
            syncTo(player);
            syncTalents(player);
            int n = PrestigeLogic.count(player, skill);
            player.sendSystemMessage(Component.literal("")
                .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("You prestiged " + skill.display() + "! Now Prestige " + n
                    + " — it's back to level 1 with " + PrestigeLogic.bonusPoints(player, skill)
                    + " bonus talent point(s).").withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }
}
