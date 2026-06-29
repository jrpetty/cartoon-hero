package com.grapplinghook;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Charge-and-release grappling hook. Hold right-click to draw (like a bow):
 * a quick tap grapples a short distance, a full {@link GrappleConfig#chargeTicks}
 * charge reaches the maximum range. On release it "casts" toward the aimed
 * block (sound + particle line) and yanks the player there, then goes on a
 * short cooldown.
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
        return 72000; // keep "drawing" until released, like a bow
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand); // begin charging
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof PlayerEntity player)) {
            return;
        }

        int ticksUsed = getMaxUseTime(stack, user) - remainingUseTicks;
        double charge = MathHelper.clamp(ticksUsed / (double) GrappleConfig.chargeTicks, 0.0, 1.0);
        double reach = MathHelper.lerp(charge, GrappleConfig.minRange, GrappleConfig.maxRange);

        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(reach));
        BlockHitResult hit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player));

        // The "cast" — pitch rises with charge.
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS,
                0.9F, 0.9F + (float) charge * 0.6F);

        if (hit.getType() != HitResult.Type.BLOCK) {
            return; // out of reach / missed — no grab
        }

        Vec3d target = hit.getPos();
        if (world instanceof ServerWorld serverWorld) {
            drawLine(serverWorld, start, target);
        }

        Vec3d toTarget = target.subtract(player.getPos());
        double distance = toTarget.length();
        double speed = Math.min(distance * GrappleConfig.pullSpeedFactor, GrappleConfig.maxPullSpeed);
        Vec3d pull = toTarget.normalize().multiply(speed).add(0.0, GrappleConfig.upwardBoost, 0.0);

        player.setVelocity(pull);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        player.getItemCooldownManager().set(this, GrappleConfig.cooldownTicks);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1.0F, 1.2F);
    }

    /** Draws a line of particles from the player to the anchor, mimicking a cast line. */
    private static void drawLine(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int points = (int) MathHelper.clamp(delta.length() * 2.0, 4, 48);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3d p = from.add(delta.multiply(t));
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
