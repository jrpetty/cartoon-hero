package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The projectile fired when you shoot a Rope Arrow from a bow or crossbow. Where
 * it sticks, it hangs a free column of climbable rope straight down.
 */
public class RopeArrowEntity extends AbstractArrow {
    private static final int MAX_LENGTH = 32;

    public RopeArrowEntity(EntityType<? extends RopeArrowEntity> type, Level level) {
        super(type, level);
    }

    public RopeArrowEntity(Level level, LivingEntity owner, ItemStack pickupItem, @Nullable ItemStack firedFromWeapon) {
        super(Gadgets.ROPE_ARROW_ENTITY.get(), owner, level, pickupItem, firedFromWeapon);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Gadgets.ROPE_ARROW.get());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide()) {
            return;
        }
        Direction side = result.getDirection();
        if (side != Direction.UP) {
            placeRope(result.getBlockPos().relative(side));
        }
        discard();
    }

    private void placeRope(BlockPos top) {
        Level level = level();
        BlockState rope = Gadgets.ROPE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = top.mutable();
        for (int i = 0; i < MAX_LENGTH; i++) {
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.canBeReplaced()) {
                break;
            }
            level.setBlockAndUpdate(pos, rope);
            pos.move(Direction.DOWN);
        }
    }
}
