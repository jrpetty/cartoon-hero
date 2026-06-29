package com.grapplinghook;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The hook itself. On use it raycasts from the player's eyes; if it strikes a
 * block within range, the player is flung toward the impact point.
 */
public class GrapplingHookItem extends Item {
    /** How far the hook can reach, in blocks. */
    private static final double MAX_RANGE = 32.0;
    /** Scales pull speed with distance. */
    private static final double PULL_FACTOR = 0.28;
    /** Hard cap on launch speed so long shots don't fling you absurdly fast. */
    private static final double MAX_PULL_SPEED = 2.6;
    /** A little lift so you arc over ledges instead of slamming the wall. */
    private static final double UPWARD_BOOST = 0.30;
    /** Ticks between uses. */
    private static final int COOLDOWN_TICKS = 8;

    public GrapplingHookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        Vec3d start = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(MAX_RANGE));

        BlockHitResult hit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                user));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }

        if (!world.isClient()) {
            Vec3d toTarget = hit.getPos().subtract(user.getPos());
            double distance = toTarget.length();

            double speed = Math.min(distance * PULL_FACTOR, MAX_PULL_SPEED);
            Vec3d pull = toTarget.normalize().multiply(speed).add(0.0, UPWARD_BOOST, 0.0);

            user.setVelocity(pull);
            user.velocityModified = true;     // sync the new velocity to the client
            user.fallDistance = 0.0F;         // don't punish the swing with fall damage

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.8F, 1.4F);
        }

        return TypedActionResult.success(stack, world.isClient());
    }
}
