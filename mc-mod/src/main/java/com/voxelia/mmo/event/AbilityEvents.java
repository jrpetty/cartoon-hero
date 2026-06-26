package com.voxelia.mmo.event;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.progression.Progression;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The two "active" skills, handled live on the server game bus:
 *   Acrobatics — trains from fall damage; grants a chance to fully dodge a hit.
 *   Fishing    — trains from catches; grants bonus luck and faster bites while
 *                a line is in the water.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class AbilityEvents {
    private AbilityEvents() {}

    private static final ResourceLocation FISHING_LUCK_ID =
        ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "fishing_luck");

    // --- Acrobatics: fall damage trains it; dodge avoids other hits ---
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();

        // Fall damage is the trainer (and is never dodged, so you can keep leveling).
        if (source.is(DamageTypes.FALL)) {
            Progression.grant(player, Skill.ACROBATICS, Math.max(2, (int) Math.ceil(event.getAmount() * 2.0)));
            return;
        }
        // Never dodge unavoidable damage (void, /kill, etc.).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        int level = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.ACROBATICS);
        double dodge = Math.min(0.95, level * VoxeliaConfig.acrobaticsDodgePerLevel());
        if (dodge > 0 && player.getRandom().nextDouble() < dodge) {
            event.setCanceled(true); // fully avoid the hit
            player.displayClientMessage(Component.literal("Dodged!").withStyle(ChatFormatting.AQUA), true);
            player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f);
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
            }
        }
    }

    // --- Fishing: each catch trains it ---
    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Progression.grant(player, Skill.FISHING, 10);
        }
    }

    // --- Fishing perks while a line is out: bonus luck + faster bites ---
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int level = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(Skill.FISHING);
        boolean fishing = player.fishing != null;
        double t = (SkillCurve.MAX_LEVEL > 1) ? (level - 1) / (double) (SkillCurve.MAX_LEVEL - 1) : 0.0;

        // dynamic luck, only while actively fishing
        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck != null) {
            if (fishing && level > 1) {
                double value = VoxeliaConfig.fishingLuckMax() * t;
                AttributeModifier existing = luck.getModifier(FISHING_LUCK_ID);
                if (existing == null || existing.amount() != value) {
                    luck.removeModifier(FISHING_LUCK_ID);
                    luck.addTransientModifier(new AttributeModifier(
                        FISHING_LUCK_ID, value, AttributeModifier.Operation.ADD_VALUE));
                }
            } else if (luck.getModifier(FISHING_LUCK_ID) != null) {
                luck.removeModifier(FISHING_LUCK_ID);
            }
        }

        // faster bites: shave extra ticks off the lure timer (up to ~fishingSpeedMax x at max level)
        FishingHook hook = player.fishing;
        if (hook != null && level > 1 && hook.timeUntilLured > 0) {
            double extraChance = (VoxeliaConfig.fishingSpeedMax() - 1.0) * t;
            if (player.getRandom().nextDouble() < extraChance) {
                hook.timeUntilLured = Math.max(0, hook.timeUntilLured - 1);
            }
        }
    }
}
