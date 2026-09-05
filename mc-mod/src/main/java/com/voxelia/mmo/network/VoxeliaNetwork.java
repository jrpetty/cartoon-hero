package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.LeaderboardStore;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.progression.SkillStats;
import com.voxelia.mmo.progression.TalentLogic;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

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
        registrar.playToClient(ProfileStatsPayload.TYPE, ProfileStatsPayload.STREAM_CODEC, ProfileStatsPayload::handle);
        registrar.playToClient(LeaderboardPayload.TYPE, LeaderboardPayload.STREAM_CODEC, LeaderboardPayload::handle);
        registrar.playToClient(MilestonePacket.TYPE, MilestonePacket.STREAM_CODEC, MilestonePacket::handle);
        registrar.playToClient(PerksSyncPayload.TYPE, PerksSyncPayload.STREAM_CODEC, PerksSyncPayload::handle);
        registrar.playToServer(AbilityPacket.TYPE, AbilityPacket.STREAM_CODEC, AbilityPacket::handle);
        registrar.playToServer(SpendTalentPacket.TYPE, SpendTalentPacket.STREAM_CODEC, SpendTalentPacket::handle);
        registrar.playToServer(ProfileRequestPacket.TYPE, ProfileRequestPacket.STREAM_CODEC, ProfileRequestPacket::handle);
        registrar.playToServer(LeaderboardRequestPacket.TYPE, LeaderboardRequestPacket.STREAM_CODEC, LeaderboardRequestPacket::handle);
    }

    /** Send the profile screen the vanilla stats it can't derive client-side (playtime, deaths, mob kills). */
    public static void sendProfile(ServerPlayer player) {
        var stats = player.getStats();
        int play = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        int deaths = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
        int kills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
        PacketDistributor.sendToPlayer(player, new ProfileStatsPayload(play, deaths, kills));
    }

    /** Answers a leaderboard request: the top ten for a skill (or the character average). */
    public static void sendLeaderboard(ServerPlayer player, int skillOrdinal) {
        Skill[] all = Skill.values();
        Skill skill = skillOrdinal >= 0 && skillOrdinal < all.length ? all[skillOrdinal] : null;

        List<LeaderboardStore.Row> rows = LeaderboardStore.top(skill, 10, player.getUUID());
        List<String> names = new ArrayList<>();
        List<Integer> data = new ArrayList<>();
        for (LeaderboardStore.Row row : rows) {
            names.add(row.name());
            data.add(row.rank());
            data.add(row.level());
            data.add(row.self() ? 1 : 0);
        }

        List<Integer> meta = new ArrayList<>();
        meta.add(skill == null ? -1 : skill.ordinal());
        meta.add(LeaderboardStore.rankOf(player.getUUID(), skill));
        meta.add(LeaderboardStore.levelFor(player.getUUID(), skill));
        meta.add(LeaderboardStore.tracked());

        PacketDistributor.sendToPlayer(player, new LeaderboardPayload(names, data, meta));
    }

    public static void syncTo(ServerPlayer player) {
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        PacketDistributor.sendToPlayer(player, new SkillsSyncPayload(skills));
    }

    /**
     * Sends what every skill is currently granting, so the menus can show real
     * numbers. Not sent on every XP tick — only when the figures can actually
     * change: login, level-up, talent spend, respawn.
     */
    public static void syncPerks(ServerPlayer player) {
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        List<String> lines = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            lines.add(SkillStats.describe(player, skill, skills.getLevel(skill)));
        }
        PacketDistributor.sendToPlayer(player, new PerksSyncPayload(lines));
    }

    public static void syncTalents(ServerPlayer player) {
        PlayerTalents talents = player.getData(VoxeliaAttachments.PLAYER_TALENTS.get());
        PacketDistributor.sendToPlayer(player, new TalentsSyncPayload(
            talents.ranks(), VoxeliaConfig.talentMaxRank(), TalentLogic.levelsPerPoint()));
    }

    /** Handle a GUI talent purchase from the client. */
    public static void handleSpendTalent(ServerPlayer player, int talentOrdinal) {
        Talent[] talents = Talent.values();
        if (talentOrdinal < 0 || talentOrdinal >= talents.length) return;
        if (TalentLogic.spend(player, talents[talentOrdinal])) {
            SkillEffects.apply(player);
            syncTo(player);
            syncTalents(player);
            syncPerks(player);
        }
    }
}
