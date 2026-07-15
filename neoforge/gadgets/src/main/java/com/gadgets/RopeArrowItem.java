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
 * A special arrow. Fire it from a bow or crossbow and, where it sticks, it hangs
 * a free column of climbable rope straight down (see {@link RopeArrowEntity}).
 */
public class RopeArrowItem extends ArrowItem {
    public RopeArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new RopeArrowEntity(level, shooter, ammo, weapon);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Tips.append(tooltip, "tip.gadgets.rope_arrow.1", "tip.gadgets.rope_arrow.2");
    }
}
