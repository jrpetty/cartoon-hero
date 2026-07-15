package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Card Scanner — hold right-click to raise it to your eye like a spyglass and
 * zoom in; any mob you look at has its Mob Trumps card projected above it and
 * its full stat block shown in the scanner readout.
 */
public class CardScannerItem extends Item {

    public CardScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        player.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ITEM_INTERACT_START);
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 1200;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player) {
            player.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Look through it at any mob to reveal its card.")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
