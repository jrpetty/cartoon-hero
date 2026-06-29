package com.grapplinghook;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Raycasts from the player's eyes; on a block hit within range, flings the
 * player toward the impact point.
 */
public class GrapplingHookItem extends Item {
    private static final double MAX_RANGE = 32.0;
    private static final double PULL_FACTOR = 0.28;
    private static final double MAX_PULL_SPEED = 2.6;
    private static final double UPWARD_BOOST = 0.30;
    private static final int COOLDOWN_TICKS = 8;

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(MAX_RANGE));

        BlockHitResult hit = level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            Vec3 toTarget = hit.getLocation().subtract(player.position());
            double distance = toTarget.length();

            double speed = Math.min(distance * PULL_FACTOR, MAX_PULL_SPEED);
            Vec3 pull = toTarget.normalize().scale(speed).add(0.0, UPWARD_BOOST, 0.0);

            player.setDeltaMovement(pull);
            player.hurtMarked = true;   // force the server to sync the new velocity to the client
            player.hasImpulse = true;
            player.fallDistance = 0.0F;

            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 1.4F);
        }

        return InteractionResultHolder.success(stack);
    }
}
