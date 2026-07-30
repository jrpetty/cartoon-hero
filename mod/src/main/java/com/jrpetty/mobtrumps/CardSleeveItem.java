package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A clear protective sleeve for one Mob Card. Hold a card in your main hand
 * with a sleeve in your off hand and right-click to slip it in; sneak
 * right-click a sleeved card to take it back out.
 *
 * <p>The sleeve is consumed onto the card rather than becoming a container that
 * holds it — see {@link CardWear}. A sleeved card never wears, but sleeving
 * repairs nothing: it preserves exactly the condition the card already has.
 */
public class CardSleeveItem extends Item {

    public CardSleeveItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Protects one card from handling wear")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Hold a card, sleeve in your off hand, right-click")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("It preserves condition — it does not repair it")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
