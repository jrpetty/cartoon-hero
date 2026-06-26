package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The single entry point for awarding skill XP and reacting to level-ups. */
public final class Progression {
    private Progression() {}

    public static void grant(ServerPlayer player, Skill skill, int baseXp) {
        if (baseXp <= 0) return;
        int amount = Math.max(1, (int) Math.round(baseXp * VoxeliaConfig.xpMultiplier()));

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
    }
}
