package com.gadgets;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A special arrow. Fire it from a bow or crossbow and, where it sticks, it hangs
 * a free column of climbable rope straight down (see {@link RopeArrowEntity}).
 */
public class RopeArrowItem extends ArrowItem {
    public RopeArrowItem(Settings settings) {
        super(settings);
    }

    @Override
    public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter, @Nullable ItemStack shotFrom) {
        return new RopeArrowEntity(world, shooter, stack, shotFrom);
    }
}
