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
 * The Torch Arrow. Fire it from a bow or crossbow and it flies like a normal
 * arrow, planting a torch where it lands (see {@link TorchArrowEntity}).
 */
public class LightArrowItem extends ArrowItem {
    public LightArrowItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter, @Nullable ItemStack shotFrom) {
        return new TorchArrowEntity(world, shooter, stack, shotFrom);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tips.append(tooltip, "tip.gadgets.light_arrow.1", "tip.gadgets.light_arrow.2");
    }
}
