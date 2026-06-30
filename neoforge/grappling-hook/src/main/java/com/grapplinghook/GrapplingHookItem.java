package com.grapplinghook;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 *   <li>Aim at a <b>block</b> → you're reeled toward it, preserving momentum so
 *       you swing. Sneak to detach mid-swing.</li>
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
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide()
                || !(entity instanceof Player player)
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int ticksUsed = getUseDuration(stack, entity) - timeLeft;
        double charge = Mth.clamp(ticksUsed / (double) GrappleConfig.chargeTicks, 0.0, 1.0);
        double reach = Mth.lerp(charge, GrappleConfig.minRange, GrappleConfig.maxRange);

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
                player.getCooldowns().addCooldown(this, GrappleConfig.cooldownTicks);
                return;
            }
        }

        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        Vec3 anchor = blockHit.getLocation();
        Vec3 dir = anchor.subtract(player.position()).normalize();
        player.setDeltaMovement(dir.scale(GrappleConfig.launchSpeed).add(0.0, GrappleConfig.upwardBoost, 0.0));
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0.0F;

        GrappleManager.start(player, anchor);
        GrappleManager.drawRope(serverLevel, start, anchor);
        player.getCooldowns().addCooldown(this, GrappleConfig.cooldownTicks);
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
