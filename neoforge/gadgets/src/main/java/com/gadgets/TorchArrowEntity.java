package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The projectile fired when you shoot a Torch Arrow from a bow or crossbow. It
 * flies like a normal arrow and plants a torch where it lands — a standing torch
 * on a floor/top face, a wall torch on a wall.
 */
public class TorchArrowEntity extends AbstractArrow {
    public TorchArrowEntity(EntityType<? extends TorchArrowEntity> type, Level level) {
        super(type, level);
    }

    public TorchArrowEntity(Level level, LivingEntity owner, ItemStack pickupItem, @Nullable ItemStack firedFromWeapon) {
        super(Gadgets.TORCH_ARROW_ENTITY.get(), owner, level, pickupItem, firedFromWeapon);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Gadgets.LIGHT_ARROW.get());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide()) {
            return;
        }
        Level level = level();
        Direction side = result.getDirection();
        if (side != Direction.DOWN) { // torches can't hang from a ceiling
            BlockPos place = result.getBlockPos().relative(side);
            BlockState torch = side == Direction.UP
                    ? Blocks.TORCH.defaultBlockState()
                    : Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side);
            BlockState existing = level.getBlockState(place);
            if ((existing.isAir() || existing.canBeReplaced()) && torch.canSurvive(level, place)) {
                level.setBlockAndUpdate(place, torch);
            }
        }
        discard();
    }
}
