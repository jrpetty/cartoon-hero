package com.gadgets;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/** A block item that carries styled how-to-use tooltip lines. */
public class TooltipBlockItem extends BlockItem {
    private final String[] tipKeys;

    public TooltipBlockItem(Block block, Settings settings, String... tipKeys) {
        super(block, settings);
        this.tipKeys = tipKeys;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Tips.append(tooltip, tipKeys);
    }
}
