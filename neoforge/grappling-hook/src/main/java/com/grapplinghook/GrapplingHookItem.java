package com.grapplinghook;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Charge-and-release grappling hook.
 *
 * <ul>
 *   <li>Hold to draw (bow-style): tap = short reach, full {@link GrappleConfig#chargeTicks}
 *       charge = max reach.</li>
 *   <li>Aim at a <b>mob</b> → it's yanked toward you.</li>
 *   <li>Aim at a <b>block</b> → you're launched toward it with a single pull;
 *       charge sets the power. Momentum carries you — no reeling in.</li>
 * </ul>
 */
public class GrapplingHookItem extends Item {

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (GrappleUpgrades.isWornOut(stack)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.grapplinghook.worn_out").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide()
                || !(entity instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int ticksUsed = getUseDuration(stack, entity) - timeLeft;
        double charge = Mth.clamp(ticksUsed / (double) GrappleConfig.chargeTicks, 0.0, 1.0);
        double reach = Mth.lerp(charge, GrappleConfig.minRange, GrappleConfig.maxRange)
                * GrappleUpgrades.rangeMultiplier(stack);

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(reach));

        BlockHitResult blockHit = serverLevel.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? start.distanceTo(blockHit.getLocation())
                : reach;

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS,
                0.9F, 0.9F + (float) charge * 0.6F);

        if (GrappleConfig.grappleEntities) {
            Entity target = findEntity(serverLevel, player, start, end, blockDist);
            if (target != null) {
                yankEntity(serverLevel, player, target);
                GrappleUpgrades.spendUse(stack);
                player.getCooldowns().addCooldown(this, cooldownFor(stack));
                return;
            }
        }

        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        // One-shot launch toward the grapple point — a single pull in that
        // direction; momentum then carries you. No reeling, no snap-back.
        Vec3 anchor = blockHit.getLocation();
        Vec3 dir = anchor.subtract(player.position()).normalize();
        double launch = Mth.lerp(charge, GrappleConfig.minLaunchSpeed, GrappleConfig.maxLaunchSpeed)
                * GrappleUpgrades.launchMultiplier(stack);
        player.setDeltaMovement(dir.scale(launch).add(0.0, GrappleConfig.upwardBoost, 0.0));
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0.0F;

        if (GrappleUpgrades.has(stack, GrappleUpgrades.IMPACT)) {
            impact(serverLevel, player, anchor);
        }
        if (GrappleUpgrades.has(stack, GrappleUpgrades.LANDING)) {
            // Ride the arc down gently instead of splattering at the far end.
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, true));
        }

        GrappleManager.drawRope(serverLevel, start, anchor);
        GrappleUpgrades.spendUse(stack);
        player.getCooldowns().addCooldown(this, cooldownFor(stack));
    }

    private static int cooldownFor(ItemStack stack) {
        return Math.max(1, (int) Math.round(GrappleConfig.cooldownTicks * GrappleUpgrades.cooldownMultiplier(stack)));
    }

    /** Impact Charge: the hook slams home, hurting whatever is at the anchor. */
    private static void impact(ServerLevel level, ServerPlayer player, Vec3 anchor) {
        DamageSource source = level.damageSources().playerAttack(player);
        AABB blast = new AABB(anchor, anchor).inflate(3.0);
        for (Entity e : level.getEntities(player, blast,
                ent -> ent instanceof LivingEntity && ent.isAlive() && !ent.isSpectator())) {
            e.hurt(source, 8.0F); // 4 hearts
        }
        level.playSound(null, anchor.x, anchor.y, anchor.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return GrappleUpgrades.wear(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * GrappleUpgrades.usesLeft(stack) / GrappleUpgrades.MAX_USES);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float left = GrappleUpgrades.usesLeft(stack) / (float) GrappleUpgrades.MAX_USES;
        return Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Condition " + GrappleUpgrades.usesLeft(stack) + "/" + GrappleUpgrades.MAX_USES)
                .withStyle(GrappleUpgrades.isWornOut(stack) ? ChatFormatting.RED : ChatFormatting.GRAY));
        for (GrappleUpgrades.Upgrade u : GrappleUpgrades.installed(stack)) {
            tooltip.add(Component.literal("✦ " + u.name()).withStyle(ChatFormatting.GOLD));
        }
    }

    private static Entity findEntity(ServerLevel level, Player player, Vec3 start, Vec3 end, double maxDist) {
        AABB searchBox = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        Entity closest = null;
        double closestSq = maxDist * maxDist;
        for (Entity e : level.getEntities(player, searchBox,
                ent -> ent instanceof LivingEntity && ent.isAlive() && !ent.isSpectator())) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(start, end);
            if (hit.isPresent()) {
                double sq = start.distanceToSqr(hit.get());
                if (sq < closestSq) {
                    closestSq = sq;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private void yankEntity(ServerLevel level, Player player, Entity target) {
        Vec3 toPlayer = player.position().subtract(target.position());
        double distance = toPlayer.length();
        double speed = Math.min(distance * GrappleConfig.entityPullFactor, GrappleConfig.entityMaxPullSpeed);
        Vec3 pull = toPlayer.normalize().scale(speed).add(0.0, 0.2, 0.0);

        target.setDeltaMovement(pull);
        target.hurtMarked = true;
        target.hasImpulse = true;

        GrappleManager.drawRope(level, player.getEyePosition(1.0F),
                target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
