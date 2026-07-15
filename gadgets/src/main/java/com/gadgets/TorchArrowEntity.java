package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
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
 * The projectile fired when you shoot a Torch Arrow from a bow or crossbow. It
 * flies like a normal arrow and plants a torch where it lands — a standing torch
 * on a floor/top face, a wall torch on a wall.
 */
public class TorchArrowEntity extends PersistentProjectileEntity {
    public TorchArrowEntity(EntityType<? extends TorchArrowEntity> type, World world) {
        super(type, world);
    }

    public TorchArrowEntity(World world, LivingEntity owner, ItemStack stack, @Nullable ItemStack shotFrom) {
        super(Gadgets.TORCH_ARROW_ENTITY, owner, world, stack, shotFrom);
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(Gadgets.LIGHT_ARROW);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (getWorld().isClient()) {
            return;
        }
        World world = getWorld();
        Direction side = blockHitResult.getSide();
        if (side != Direction.DOWN) { // torches can't hang from a ceiling
            BlockPos place = blockHitResult.getBlockPos().offset(side);
            BlockState torch = side == Direction.UP
                    ? Blocks.TORCH.getDefaultState()
                    : Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, side);
            BlockState existing = world.getBlockState(place);
            if ((existing.isAir() || existing.isReplaceable()) && torch.canPlaceAt(world, place)) {
                world.setBlockState(place, torch);
            }
        }
        discard();
    }
}
