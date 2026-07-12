package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The collection book: tracks all 81 mob cards you have ever collected. */
public class CollectionBookItem extends Item {

    public CollectionBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openCollectionBook();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Track all 81 mob cards you've collected.")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("File loose cards away to keep your inventory tidy —")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("open it and hit Store, or /mobtrumps store.")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
