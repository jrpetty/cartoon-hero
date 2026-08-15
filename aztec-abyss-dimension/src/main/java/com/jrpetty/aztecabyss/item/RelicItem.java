package com.jrpetty.aztecabyss.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A relic that is not a weapon, but still has something to say.
 *
 * <p>The companion to {@link RelicSword}: same job, for the things you carry
 * rather than swing.
 */
public class RelicItem extends Item {

    private final List<Component> lore;

    public RelicItem(Properties properties, String... lines) {
        super(properties);
        this.lore = java.util.Arrays.stream(lines)
                .map(line -> (Component) Component.literal(line))
                .toList();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (Component line : lore) {
            tooltip.add(line.copy().withStyle(ChatFormatting.RESET));
        }
    }
}
