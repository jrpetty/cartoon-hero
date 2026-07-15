package com.gadgets;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Torch Arrow. Fire it from a bow or crossbow and it flies like a normal
 * arrow, planting a torch where it lands (see {@link TorchArrowEntity}).
 */
public class LightArrowItem extends ArrowItem {
    public LightArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new TorchArrowEntity(level, shooter, ammo, weapon);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Tips.append(tooltip, "tip.gadgets.light_arrow.1", "tip.gadgets.light_arrow.2");
    }
}
