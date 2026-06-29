package com.cartoonhero;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Right-click to transform into a cartoon hero: a burst of Speed, Jump and
 * Regeneration, a triumphant level-up chime, and a cooldown.
 */
public class HeroEmblemItem extends Item {
    private static final int BUFF_DURATION_TICKS = 30 * 20;
    private static final int REGEN_DURATION_TICKS = 5 * 20;
    private static final int COOLDOWN_TICKS = 45 * 20;

    public HeroEmblemItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_DURATION_TICKS, 1));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, BUFF_DURATION_TICKS, 1));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION_TICKS, 0));

            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cartoonhero.hero_emblem").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
