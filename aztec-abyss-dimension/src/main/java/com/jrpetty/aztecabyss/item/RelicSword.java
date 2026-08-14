package com.jrpetty.aztecabyss.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A weapon with something to say about itself.
 *
 * <p>Both of the relics do something invisible - one takes armour off the
 * table, the other poisons - and a power a player cannot see is a power they
 * will never trust or plan around. Vanilla puts attack damage on the tooltip;
 * these put the rest of the truth there too, in the item's own voice.
 */
public class RelicSword extends SwordItem {

    private final List<Component> lore;

    public RelicSword(Tier tier, Properties properties, String... lines) {
        super(tier, properties);
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
