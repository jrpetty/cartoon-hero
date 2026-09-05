package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.network.MilestonePacket;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/** The single entry point for awarding skill XP and reacting to level-ups. */
public final class Progression {
    private Progression() {}

    public static void grant(ServerPlayer player, Skill skill, int baseXp) {
        if (baseXp <= 0) return;
        double mult = VoxeliaConfig.xpMultiplier() * TalentLogic.xpBonus(player, skill);
        int amount = Math.max(1, (int) Math.round(baseXp * mult));

        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        int before = skills.getLevel(skill);
        skills.addXp(skill, amount);
        // re-set to flag the attachment dirty for persistence
        player.setData(VoxeliaAttachments.PLAYER_SKILLS.get(), skills);

        int after = skills.getLevel(skill);
        if (after > before) onLevelUp(player, skill, after);

        VoxeliaNetwork.syncTo(player);
    }

    private static void onLevelUp(ServerPlayer player, Skill skill, int level) {
        SkillEffects.apply(player);
        LeaderboardStore.record(player);
        VoxeliaNetwork.syncPerks(player);
        player.sendSystemMessage(Component.literal("")
            .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(skill.display() + " reached level " + level + "!")
                .withStyle(ChatFormatting.YELLOW)));
        player.level().playSound(null, player.blockPosition(),
            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);

        // A talent point lands every N levels — say so, or it sits unnoticed.
        int per = TalentLogic.levelsPerPoint();
        if (per > 0 && level % per == 0) {
            player.sendSystemMessage(Component.literal("")
                .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Talent point earned in " + skill.display() + "! ")
                    .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Open your skills and pick Menu ▸ Talent Tree to spend it.")
                    .withStyle(ChatFormatting.GRAY)));
            player.displayClientMessage(
                Component.literal("✦ +1 " + skill.display() + " talent point")
                    .withStyle(ChatFormatting.GREEN), true);
            player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.9f);
        }

        // Level-gated perks used to arrive in silence — announce each one.
        for (Milestones.Kind kind : Milestones.at(skill, level)) {
            PacketDistributor.sendToPlayer(player, new MilestonePacket(skill.ordinal(), kind.ordinal(), level));
            player.sendSystemMessage(Component.literal("")
                .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(perkName(skill, kind) + " unlocked! ")
                    .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("(" + skill.display() + " " + level + ")")
                    .withStyle(ChatFormatting.GRAY)));
        }

        // celebratory particles
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.7, 0.5, 0.05);
        }
    }

    /** Chat-side name for a perk; the toast builds its own richer wording client-side. */
    private static String perkName(Skill skill, Milestones.Kind kind) {
        return switch (kind) {
            case ABILITY -> skill.abilityName();
            case HASTE -> "Haste";
            case TELEKINESIS -> "Telekinesis";
            case LAST_STAND -> "Last Stand";
            case WELL_FED -> "Well Fed";
        };
    }
}
