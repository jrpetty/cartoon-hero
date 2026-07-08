package com.grapplinghook;

import java.util.Optional;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

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

    public GrapplingHookItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity userEntity, int remainingUseTicks) {
        if (world.isClient()
                || !(userEntity instanceof ServerPlayerEntity player)
                || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        int ticksUsed = getMaxUseTime(stack, userEntity) - remainingUseTicks;
        double charge = MathHelper.clamp(ticksUsed / (double) GrappleConfig.chargeTicks, 0.0, 1.0);
        double reach = MathHelper.lerp(charge, GrappleConfig.minRange, GrappleConfig.maxRange);

        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(reach));

        BlockHitResult blockHit = serverWorld.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? start.distanceTo(blockHit.getPos())
                : reach;

        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS,
                0.9F, 0.9F + (float) charge * 0.6F);

        // Mob in the way (closer than the block)? Yank it toward us.
        if (GrappleConfig.grappleEntities) {
            Entity target = findEntity(serverWorld, player, start, end, blockDist);
            if (target != null) {
                yankEntity(serverWorld, player, target);
                player.getItemCooldownManager().set(this, GrappleConfig.cooldownTicks);
                return;
            }
        }

        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return; // out of reach / missed
        }

        // One-shot launch toward the grapple point — a single pull in that
        // direction; momentum then carries you. No reeling, no snap-back.
        Vec3d anchor = blockHit.getPos();
        Vec3d dir = anchor.subtract(player.getPos()).normalize();
        double launch = MathHelper.lerp(charge, GrappleConfig.minLaunchSpeed, GrappleConfig.maxLaunchSpeed);
        player.setVelocity(dir.multiply(launch).add(0.0, GrappleConfig.upwardBoost, 0.0));
        player.velocityModified = true;
        player.fallDistance = 0.0F;

        GrappleManager.drawRope(serverWorld, start, anchor);
        player.getItemCooldownManager().set(this, GrappleConfig.cooldownTicks);
    }

    /** Closest living entity whose hitbox the aim ray crosses within maxDist. */
    private static Entity findEntity(ServerWorld world, PlayerEntity player, Vec3d start, Vec3d end, double maxDist) {
        Box searchBox = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
        Entity closest = null;
        double closestSq = maxDist * maxDist;
        for (Entity e : world.getOtherEntities(player, searchBox,
                ent -> ent instanceof LivingEntity && ent.isAlive() && !ent.isSpectator())) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(start, end);
            if (hit.isPresent()) {
                double sq = start.squaredDistanceTo(hit.get());
                if (sq < closestSq) {
                    closestSq = sq;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private void yankEntity(ServerWorld world, PlayerEntity player, Entity target) {
        Vec3d toPlayer = player.getPos().subtract(target.getPos());
        double distance = toPlayer.length();
        double speed = Math.min(distance * GrappleConfig.entityPullFactor, GrappleConfig.entityMaxPullSpeed);
        Vec3d pull = toPlayer.normalize().multiply(speed).add(0.0, 0.2, 0.0);

        target.setVelocity(pull);
        target.velocityModified = true;

        GrappleManager.drawRope(world, player.getEyePos(),
                target.getPos().add(0.0, target.getHeight() * 0.5, 0.0));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1.0F, 1.2F);
    }
}
