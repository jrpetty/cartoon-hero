package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The single entry point for awarding skill XP and reacting to level-ups. */
public final class Progression {
    private Progression() {}

    public static void grant(ServerPlayer player, Skill skill, int baseXp) {
        if (baseXp <= 0) return;
        double mult = VoxeliaConfig.xpMultiplier() * TalentLogic.prodigyMultiplier(player, skill);
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
        player.sendSystemMessage(Component.literal("")
            .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(skill.display() + " reached level " + level + "!")
                .withStyle(ChatFormatting.YELLOW)));
        player.level().playSound(null, player.blockPosition(),
            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);

        // on-screen title
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("Level Up!").withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal(skill.display() + " " + level)
                .withStyle(style -> style.withColor(skill.color()))));

        // celebratory particles
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.7, 0.5, 0.05);
        }
    }
}
