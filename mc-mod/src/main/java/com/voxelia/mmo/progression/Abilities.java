package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.network.AbilityCooldownPacket;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * One signature active ability per skill, dispatched by the skill's ordinal.
 * Balanced by design: short buffs / utility, level-gated, on long cooldowns.
 */
public final class Abilities {
    private Abilities() {}

    // playerUUID -> next-allowed game time, one slot per skill ordinal
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    public static void trigger(ServerPlayer p, int skillOrdinal) {
        Skill[] all = Skill.values();
        if (skillOrdinal < 0 || skillOrdinal >= all.length) return;
        switch (all[skillOrdinal]) {
            case MINING     -> cast(p, Skill.MINING, VoxeliaConfig.minersFocusLevel(), VoxeliaConfig.minersFocusCooldownSeconds(), () -> minersFocus(p));
            case FORAGING   -> cast(p, Skill.FORAGING, VoxeliaConfig.overgrowthLevel(), VoxeliaConfig.overgrowthCooldownSeconds(), () -> overgrowth(p));
            case COMBAT     -> cast(p, Skill.COMBAT, VoxeliaConfig.frenzyLevel(), VoxeliaConfig.frenzyCooldownSeconds(), () -> frenzy(p));
            case FARMING    -> cast(p, Skill.FARMING, VoxeliaConfig.heartyMealLevel(), VoxeliaConfig.heartyMealCooldownSeconds(), () -> heartyMeal(p));
            case ACROBATICS -> cast(p, Skill.ACROBATICS, VoxeliaConfig.leapLevel(), VoxeliaConfig.leapCooldownSeconds(), () -> leap(p));
            case FISHING    -> cast(p, Skill.FISHING, VoxeliaConfig.reelLevel(), VoxeliaConfig.reelCooldownSeconds(), () -> reel(p));
        }
    }

    // ---- framework ----
    private static void cast(ServerPlayer p, Skill skill, int unlock, int cooldown, BooleanSupplier effect) {
        if (unlock <= 0) {
            p.displayClientMessage(Component.literal(skill.abilityName() + " is disabled").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill) < unlock) {
            p.displayClientMessage(Component.literal(skill.abilityName() + " unlocks at " + skill.display() + " " + unlock)
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        int slot = skill.ordinal();
        long remaining = cd(p)[slot] - p.level().getGameTime();
        if (remaining > 0) {
            p.displayClientMessage(Component.literal(skill.abilityName() + " — cooldown " + (remaining / 20 + 1) + "s")
                .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (effect.getAsBoolean()) {
            int durationTicks = cooldown * 20;
            cd(p)[slot] = p.level().getGameTime() + durationTicks;
            PacketDistributor.sendToPlayer(p, new AbilityCooldownPacket(slot, durationTicks));
            p.serverLevel().sendParticles(ParticleTypes.ENCHANT,
                p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.4, 0.7, 0.4, 0.05);
        }
    }

    private static long[] cd(ServerPlayer p) {
        return COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[Skill.values().length]);
    }

    private static boolean announce(ServerPlayer p, String name, ChatFormatting color, SoundEvent sound, float pitch) {
        p.displayClientMessage(Component.literal(name + "!").withStyle(color), true);
        p.level().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, 0.7f, pitch);
        return true;
    }

    // ---- abilities ----
    private static boolean minersFocus(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, false, false, true));
        return announce(p, "Miner's Focus", ChatFormatting.GRAY, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f);
    }

    private static boolean frenzy(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true, true));
        return announce(p, "Frenzy", ChatFormatting.RED, SoundEvents.RAVAGER_ROAR, 1.5f);
    }

    private static boolean heartyMeal(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20, 0, false, false, true));
        return announce(p, "Hearty Meal", ChatFormatting.GOLD, SoundEvents.PLAYER_BURP, 1.0f);
    }

    private static boolean overgrowth(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos center = p.blockPosition();
        int radius = 3, applied = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
            if (applied >= 16) break;
            if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos.immutable(), p)) applied++;
        }
        if (applied == 0) {
            p.displayClientMessage(Component.literal("No plants nearby to nurture").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return announce(p, "Overgrowth", ChatFormatting.GREEN, SoundEvents.BONE_MEAL_USE, 1.2f);
    }

    private static boolean leap(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(new Vec3(look.x * 1.1, 0.55 + Math.max(0.0, look.y) * 0.6, look.z * 1.1));
        p.hurtMarked = true;
        p.fallDistance = 0.0f;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
        return announce(p, "Leap", ChatFormatting.AQUA, SoundEvents.PHANTOM_FLAP, 1.5f);
    }

    private static boolean reel(ServerPlayer p) {
        FishingHook hook = p.fishing;
        if (hook == null) {
            p.displayClientMessage(Component.literal("Cast a line first").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Entity hooked = hook.getHookedIn();
        if (hooked != null) {
            Vec3 pull = p.position().subtract(hooked.position()).normalize().scale(1.2);
            hooked.setDeltaMovement(pull.x, pull.y + 0.3, pull.z);
            hooked.hurtMarked = true;
        } else {
            Vec3 pull = hook.position().subtract(p.position()).normalize().scale(0.9);
            p.setDeltaMovement(pull.x, pull.y + 0.3, pull.z);
            p.hurtMarked = true;
            p.fallDistance = 0.0f;
        }
        return announce(p, "Reel", ChatFormatting.AQUA, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.4f);
    }
}
