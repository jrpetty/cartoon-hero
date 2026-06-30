package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click while aiming at a wall to "fire" a rope: a column of ladders
 * trails down the wall face from the impact point.
 */
public class RopeArrowItem extends Item {
    private static final double RANGE = 24.0;
    private static final int MAX_LENGTH = 32;

    public RopeArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        Direction side = hit.getDirection();
        if (side.getAxis().isVertical()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            int placed = placeRope(level, hit.getBlockPos().relative(side), side);
            if (placed > 0) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8F, 1.2F);
                player.getCooldowns().addCooldown(this, 10);
            } else {
                return InteractionResultHolder.pass(stack);
            }
        }
        return InteractionResultHolder.success(stack, level.isClientSide());
    }

    private static int placeRope(Level level, BlockPos top, Direction wallSide) {
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, wallSide);
        BlockPos.MutableBlockPos pos = top.mutable();
        int placed = 0;
        for (int i = 0; i < MAX_LENGTH; i++) {
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.canBeReplaced()) {
                break;
            }
            BlockPos wall = pos.relative(wallSide.getOpposite());
            if (!level.getBlockState(wall).isFaceSturdy(level, wall, wallSide)) {
                break;
            }
            level.setBlockAndUpdate(pos, ladder);
            placed++;
            pos.move(Direction.DOWN);
        }
        return placed;
    }
}
