package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Right-click while aiming to "fire" a rope: a free-hanging column of climbable
 * rope trails straight down from the impact point. Grapple's cheap cousin.
 */
public class RopeArrowItem extends Item {
    private static final double RANGE = 24.0;
    private static final int MAX_LENGTH = 32;

    public RopeArrowItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        Vec3d start = user.getEyePos();
        Vec3d end = start.add(user.getRotationVec(1.0F).multiply(RANGE));
        BlockHitResult hit = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }
        Direction side = hit.getSide();
        if (side == Direction.UP) {
            return TypedActionResult.pass(stack); // don't hang a rope off the top of a floor
        }

        if (!world.isClient()) {
            int placed = placeRope(world, hit.getBlockPos().offset(side));
            if (placed > 0) {
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8F, 1.2F);
                user.getItemCooldownManager().set(this, 10);
            } else {
                return TypedActionResult.pass(stack);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    private static int placeRope(World world, BlockPos top) {
        BlockState rope = Gadgets.ROPE.getDefaultState();
        BlockPos.Mutable pos = top.mutableCopy();
        int placed = 0;
        for (int i = 0; i < MAX_LENGTH; i++) {
            BlockState here = world.getBlockState(pos);
            if (!here.isAir() && !here.isReplaceable()) {
                break; // rope hangs down until it meets something
            }
            world.setBlockState(pos, rope);
            placed++;
            pos.move(Direction.DOWN);
        }
        return placed;
    }
}
