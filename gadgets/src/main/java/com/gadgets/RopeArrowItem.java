package com.gadgets;

import java.util.List;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A special arrow. Fire it from a bow or crossbow and, where it sticks, it hangs
 * a free column of climbable rope straight down (see {@link RopeArrowEntity}).
 */
public class RopeArrowItem extends ArrowItem {
    public RopeArrowItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter, @Nullable ItemStack shotFrom) {
        return new RopeArrowEntity(world, shooter, stack, shotFrom);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tips.append(tooltip, "tip.gadgets.rope_arrow.1", "tip.gadgets.rope_arrow.2");
    }
}
