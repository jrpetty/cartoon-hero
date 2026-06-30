package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
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
 * Right-click while aiming at a wall to "fire" a rope: a column of ladders
 * trails down the wall face from the impact point. Grapple's cheap cousin.
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
        if (side.getAxis().isVertical()) {
            return TypedActionResult.pass(stack); // need a vertical wall to hang from
        }

        if (!world.isClient()) {
            int placed = placeRope(world, hit.getBlockPos().offset(side), side);
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

    private static int placeRope(World world, BlockPos top, Direction wallSide) {
        BlockState ladder = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, wallSide);
        BlockPos.Mutable pos = top.mutableCopy();
        int placed = 0;
        for (int i = 0; i < MAX_LENGTH; i++) {
            BlockState here = world.getBlockState(pos);
            if (!here.isAir() && !here.isReplaceable()) {
                break;
            }
            BlockPos wall = pos.offset(wallSide.getOpposite());
            if (!world.getBlockState(wall).isSideSolidFullSquare(world, wall, wallSide)) {
                break; // nothing to hang the rung from
            }
            world.setBlockState(pos, ladder);
            placed++;
            pos.move(Direction.DOWN);
        }
        return placed;
    }
}
