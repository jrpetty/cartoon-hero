package com.cartoonhero.item;

import java.util.List;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Right-click to transform into a cartoon hero: a burst of Speed, Jump Boost
 * and Regeneration, a triumphant level-up chime, and a cooldown so you can't
 * spam it.
 */
public class HeroEmblemItem extends Item {
    private static final int BUFF_DURATION_TICKS = 30 * 20; // 30 seconds
    private static final int REGEN_DURATION_TICKS = 5 * 20; // 5 seconds
    private static final int COOLDOWN_TICKS = 45 * 20;      // 45 seconds

    public HeroEmblemItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Only apply effects on the server; the client just plays along.
        if (!world.isClient()) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, BUFF_DURATION_TICKS, 1));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, BUFF_DURATION_TICKS, 1));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, REGEN_DURATION_TICKS, 0));

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

            world.playSound(
                    null,
                    user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS,
                    1.0F, 1.2F);
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.cartoonhero.hero_emblem").formatted(Formatting.GOLD));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
