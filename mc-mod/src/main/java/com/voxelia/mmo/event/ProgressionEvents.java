package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hooks vanilla gameplay events to skill XP (server side, game event bus):
 * breaking blocks -> Mining/Foraging/Farming, killing mobs -> Combat. Also
 * re-applies stat effects + syncs on login and respawn.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class ProgressionEvents {
    private ProgressionEvents() {}

    // XP lost on the last death, surfaced to the player once they respawn.
    private static final Map<UUID, Integer> PENDING_DEATH_LOSS = new HashMap<>();

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();

        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            Progression.grant(player, Skill.FORAGING, 6);
        } else if (state.is(BlockTags.CROPS)) {
            Progression.grant(player, Skill.FARMING, 5);
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            Progression.grant(player, Skill.EXCAVATION, 5);
        } else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            Progression.grant(player, Skill.MINING, state.is(Tags.Blocks.ORES) ? 12 : 5);
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        int base = (int) Math.max(2, Math.ceil(victim.getMaxHealth() / 2.0));
        if (victim instanceof Enemy) base += 4; // hostile bonus
        Progression.grant(player, Skill.COMBAT, base);

        // Life steal perk: heal on kill, scaled by Combat level.
        int combatLevel = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.COMBAT);
        float heal = (float) (combatLevel * com.voxelia.mmo.config.VoxeliaConfig.combatLifeStealPerLevel());
        if (heal > 0) player.heal(heal);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillEffects.apply(player);
            VoxeliaNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillEffects.apply(player);
            VoxeliaNetwork.syncTo(player);
            Integer lost = PENDING_DEATH_LOSS.remove(player.getUUID());
            if (lost != null && lost > 0) {
                player.sendSystemMessage(Component.literal("")
                    .append(Component.literal("[Voxelia] ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("You lost " + lost + " skill XP on death.")
                        .withStyle(ChatFormatting.RED)));
            }
        }
    }

    /** Apply the configured skill-XP death penalty when a player respawns after dying. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        double pct = VoxeliaConfig.deathXpLossPercent();
        if (pct <= 0) return;
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;

        PlayerSkills original = event.getOriginal().getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        PlayerSkills reduced = PlayerSkills.fromMap(original.toMap());
        reduced.loseFraction(pct);
        newPlayer.setData(VoxeliaAttachments.PLAYER_SKILLS.get(), reduced);

        int lost = original.totalXp() - reduced.totalXp();
        if (lost > 0) PENDING_DEATH_LOSS.put(newPlayer.getUUID(), lost);
        // SkillEffects + sync + the message happen in onRespawn, which fires after Clone.
    }
}

