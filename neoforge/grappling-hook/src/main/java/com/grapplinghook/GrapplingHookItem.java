package com.grapplinghook;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Charge-and-release grappling hook. Hold right-click to draw (like a bow):
 * a quick tap grapples a short distance, a full {@link GrappleConfig#chargeTicks}
 * charge reaches the maximum range. On release it "casts" toward the aimed
 * block (sound + particle line) and yanks the player there, then goes on a
 * short cooldown.
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
        return 72000; // keep "drawing" until released, like a bow
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand); // begin charging
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }

        int ticksUsed = getUseDuration(stack, entity) - timeLeft;
        double charge = Mth.clamp(ticksUsed / (double) GrappleConfig.chargeTicks, 0.0, 1.0);
        double reach = Mth.lerp(charge, GrappleConfig.minRange, GrappleConfig.maxRange);

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(reach));
        BlockHitResult hit = level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));

        // The "cast" — pitch rises with charge.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS,
                0.9F, 0.9F + (float) charge * 0.6F);

        if (hit.getType() != HitResult.Type.BLOCK) {
            return; // out of reach / missed — no grab
        }

        Vec3 target = hit.getLocation();
        if (level instanceof ServerLevel serverLevel) {
            drawLine(serverLevel, start, target);
        }

        Vec3 toTarget = target.subtract(player.position());
        double distance = toTarget.length();
        double speed = Math.min(distance * GrappleConfig.pullSpeedFactor, GrappleConfig.maxPullSpeed);
        Vec3 pull = toTarget.normalize().scale(speed).add(0.0, GrappleConfig.upwardBoost, 0.0);

        player.setDeltaMovement(pull);
        player.hurtMarked = true;   // sync velocity to the client
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
        player.getCooldowns().addCooldown(this, GrappleConfig.cooldownTicks);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** Draws a line of particles from the player to the anchor, mimicking a cast line. */
    private static void drawLine(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int points = (int) Mth.clamp(delta.length() * 2.0, 4, 48);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3 p = from.add(delta.scale(t));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
