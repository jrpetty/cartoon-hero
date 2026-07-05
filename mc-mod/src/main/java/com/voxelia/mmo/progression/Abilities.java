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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
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
    // playerUUID -> game time until which Defense's Bulwark deflect is active
    private static final Map<UUID, Long> BULWARK_UNTIL = new HashMap<>();

    /** True while the player's Bulwark deflect window is open (read by the damage handler). */
    public static boolean isBulwarkActive(ServerPlayer p) {
        Long until = BULWARK_UNTIL.get(p.getUUID());
        return until != null && until > p.level().getGameTime();
    }

    public static void trigger(ServerPlayer p, int skillOrdinal) {
        Skill[] all = Skill.values();
        if (skillOrdinal < 0 || skillOrdinal >= all.length) return;
        switch (all[skillOrdinal]) {
            case MINING     -> cast(p, Skill.MINING, VoxeliaConfig.minersFocusLevel(), VoxeliaConfig.minersFocusCooldownSeconds(), () -> minersFocus(p));
            case FORAGING   -> cast(p, Skill.FORAGING, VoxeliaConfig.overgrowthLevel(), VoxeliaConfig.overgrowthCooldownSeconds(), () -> overgrowth(p));
            case COMBAT     -> cast(p, Skill.COMBAT, VoxeliaConfig.frenzyLevel(), VoxeliaConfig.frenzyCooldownSeconds(), () -> frenzy(p));
            case FARMING    -> cast(p, Skill.FARMING, VoxeliaConfig.heartyMealLevel(), VoxeliaConfig.heartyMealCooldownSeconds(), () -> heartyMeal(p));
            case ACROBATICS -> cast(p, Skill.ACROBATICS, VoxeliaConfig.leapLevel(), VoxeliaConfig.leapCooldownSeconds(), () -> leap(p));
            case FISHING    -> cast(p, Skill.FISHING, VoxeliaConfig.maelstromLevel(), VoxeliaConfig.maelstromCooldownSeconds(), () -> maelstrom(p));
            case EXCAVATION -> cast(p, Skill.EXCAVATION, VoxeliaConfig.excavateLevel(), VoxeliaConfig.excavateCooldownSeconds(), () -> excavate(p));
            case DEFENSE    -> cast(p, Skill.DEFENSE, VoxeliaConfig.bulwarkLevel(), VoxeliaConfig.bulwarkCooldownSeconds(), () -> bulwark(p));
            case COOKING    -> cast(p, Skill.COOKING, VoxeliaConfig.feastLevel(), VoxeliaConfig.feastCooldownSeconds(), () -> feast(p));
            case ALCHEMY    -> cast(p, Skill.ALCHEMY, VoxeliaConfig.panaceaLevel(), VoxeliaConfig.panaceaCooldownSeconds(), () -> panacea(p));
            case ARCHERY    -> cast(p, Skill.ARCHERY, VoxeliaConfig.volleyLevel(), VoxeliaConfig.volleyCooldownSeconds(), () -> volley(p));
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

    /** Fishing: a whirlpool that drags every nearby creature toward you and slows them. */
    private static boolean maelstrom(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double radius = 10.0;
        var targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
            e -> e != p && e.isAlive() && !e.isSpectator());
        if (targets.isEmpty()) {
            p.displayClientMessage(Component.literal("No creatures caught in the current").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        for (LivingEntity e : targets) {
            Vec3 toPlayer = p.position().subtract(e.position());
            double dist = toPlayer.length();
            if (dist < 0.1) continue;
            Vec3 vel = toPlayer.normalize().scale(Math.min(1.4, 0.4 + dist * 0.11));
            e.setDeltaMovement(vel.x, Math.max(0.25, vel.y + 0.25), vel.z);
            e.hurtMarked = true; // sync the pull to clients (matters for players too)
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true, true));
        }
        level.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.2, p.getZ(),
            60, radius / 2.0, 0.4, radius / 2.0, 0.1);
        return announce(p, "Maelstrom", ChatFormatting.AQUA, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7f);
    }

    // ---- ultimates for the newer skills (powerful, long cooldowns) ----

    /** Excavation: instantly clear the shovel-mineable blocks around you (drops included). */
    private static boolean excavate(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos center = p.blockPosition();
        int radius = 3, broken = 0, cap = 120;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (broken >= cap) break;
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || !st.is(BlockTags.MINEABLE_WITH_SHOVEL)) continue;
            if (st.getDestroySpeed(level, pos) < 0) continue; // unbreakable (bedrock, etc.)
            if (level.destroyBlock(pos.immutable(), true, p)) broken++;
        }
        if (broken == 0) {
            p.displayClientMessage(Component.literal("Nothing to excavate here").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return announce(p, "Excavate", ChatFormatting.GOLD, SoundEvents.SHOVEL_FLATTEN, 0.8f);
    }

    /** Defense: a 5-second deflect — take almost no damage and reflect the blow (see AbilityEvents). */
    private static boolean bulwark(ServerPlayer p) {
        BULWARK_UNTIL.put(p.getUUID(), p.level().getGameTime() + 100L); // 5 seconds
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, true, true)); // planted
        return announce(p, "Bulwark", ChatFormatting.AQUA, SoundEvents.ANVIL_LAND, 0.8f);
    }

    /** Cooking: a full heal, refilled hunger, and a short regen + absorption. */
    private static boolean feast(ServerPlayer p) {
        p.heal(p.getMaxHealth());
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 60, 4, false, false, true));  // refill hunger
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1, false, true, true));   // +4 hearts, 15s
        return announce(p, "Feast", ChatFormatting.GOLD, SoundEvents.PLAYER_BURP, 1.0f);
    }

    /** Alchemy: cleanse every harmful effect and ward yourself briefly. */
    private static boolean panacea(ServerPlayer p) {
        for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects())) {
            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(e.getEffect());
        }
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 1, false, true, true)); // Resistance II, 8s
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0, false, true, true));
        return announce(p, "Panacea", ChatFormatting.LIGHT_PURPLE, SoundEvents.BREWING_STAND_BREW, 1.4f);
    }

    /** Archery: loose a fan of seven arrows in the direction you're facing. */
    private static boolean volley(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int count = 7;
        for (int i = 0; i < count; i++) {
            float spread = (i - count / 2) * 8.0f; // -24° .. +24°
            Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), null);
            arrow.shootFromRotation(p, p.getXRot(), p.getYRot() + spread, 0.0f, 3.0f, 0.5f);
            arrow.setBaseDamage(6.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED; // no free-arrow farming
            level.addFreshEntity(arrow);
        }
        return announce(p, "Volley", ChatFormatting.DARK_GREEN, SoundEvents.ARROW_SHOOT, 1.0f);
    }
}
