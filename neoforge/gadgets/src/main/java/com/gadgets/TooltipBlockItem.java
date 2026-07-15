package com.gadgets;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A block item that carries styled how-to-use tooltip lines. */
public class TooltipBlockItem extends BlockItem {
    private final String[] tipKeys;

    public TooltipBlockItem(Block block, Properties properties, String... tipKeys) {
        super(block, properties);
        this.tipKeys = tipKeys;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Tips.append(tooltip, tipKeys);
    }
}
