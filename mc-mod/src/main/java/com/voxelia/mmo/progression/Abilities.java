package com.voxelia.mmo.progression;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown-gated active abilities (balanced by long cooldowns + level gates):
 *   FRENZY (Combat)    — short Strength + Speed burst.
 *   LEAP   (Acrobatics) — a forward/upward dash with a safe landing.
 */
public final class Abilities {
    private Abilities() {}

    public static final int FRENZY = 0;
    public static final int LEAP = 1;

    // playerUUID -> next-allowed game time per ability slot
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    public static void trigger(ServerPlayer player, int ability) {
        switch (ability) {
            case FRENZY -> frenzy(player);
            case LEAP -> leap(player);
            default -> { }
        }
    }

    private static long[] cd(ServerPlayer p) {
        return COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[]{0L, 0L});
    }

    private static boolean onCooldown(ServerPlayer p, int slot) {
        long remaining = cd(p)[slot] - p.level().getGameTime();
        if (remaining > 0) {
            p.displayClientMessage(Component.literal("On cooldown (" + (remaining / 20 + 1) + "s)")
                .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        return false;
    }

    private static void startCooldown(ServerPlayer p, int slot, int seconds) {
        cd(p)[slot] = p.level().getGameTime() + seconds * 20L;
    }

    private static boolean gate(ServerPlayer p, Skill skill, int unlock, String name) {
        if (unlock <= 0) return false;
        if (p.getData(VoxeliaAttachments.PLAYER_SKILLS.get()).getLevel(skill) < unlock) {
            p.displayClientMessage(Component.literal(name + " unlocks at " + skill.display() + " " + unlock)
                .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    private static void frenzy(ServerPlayer p) {
        if (!gate(p, Skill.COMBAT, VoxeliaConfig.frenzyLevel(), "Frenzy")) return;
        if (onCooldown(p, FRENZY)) return;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true, true));
        p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.5f);
        p.displayClientMessage(Component.literal("Frenzy!").withStyle(ChatFormatting.RED), true);
        startCooldown(p, FRENZY, VoxeliaConfig.frenzyCooldownSeconds());
    }

    private static void leap(ServerPlayer p) {
        if (!gate(p, Skill.ACROBATICS, VoxeliaConfig.leapLevel(), "Leap")) return;
        if (onCooldown(p, LEAP)) return;
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(new Vec3(look.x * 1.1, 0.55 + Math.max(0.0, look.y) * 0.6, look.z * 1.1));
        p.hurtMarked = true;      // push the new velocity to the client
        p.fallDistance = 0.0f;    // safe landing
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.5f);
        p.displayClientMessage(Component.literal("Leap!").withStyle(ChatFormatting.AQUA), true);
        startCooldown(p, LEAP, VoxeliaConfig.leapCooldownSeconds());
    }
}
