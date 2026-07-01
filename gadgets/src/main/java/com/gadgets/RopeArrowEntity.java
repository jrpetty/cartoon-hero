package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The projectile fired when you shoot a Rope Arrow from a bow or crossbow. Where
 * it sticks, it hangs a free column of climbable rope straight down.
 */
public class RopeArrowEntity extends PersistentProjectileEntity {
    private static final int MAX_LENGTH = 32;

    public RopeArrowEntity(EntityType<? extends RopeArrowEntity> type, World world) {
        super(type, world);
    }

    public RopeArrowEntity(World world, LivingEntity owner, ItemStack stack, @Nullable ItemStack shotFrom) {
        super(Gadgets.ROPE_ARROW_ENTITY, owner, world, stack, shotFrom);
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(Gadgets.ROPE_ARROW);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (getWorld().isClient()) {
            return;
        }
        Direction side = blockHitResult.getSide();
        if (side != Direction.UP) {
            placeRope(blockHitResult.getBlockPos().offset(side));
        }
        discard();
    }

    private void placeRope(BlockPos top) {
        World world = getWorld();
        BlockState rope = Gadgets.ROPE.getDefaultState();
        BlockPos.Mutable pos = top.mutableCopy();
        for (int i = 0; i < MAX_LENGTH; i++) {
            BlockState here = world.getBlockState(pos);
            if (!here.isAir() && !here.isReplaceable()) {
                break;
            }
            world.setBlockState(pos, rope);
            pos.move(Direction.DOWN);
        }
    }
}
