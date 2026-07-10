package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.jrpetty.mobtrumps.game.Tier;
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

/**
 * A single collectable mob card. One item covers all 81 mobs — the mob is
 * stored in the {@link ModItems#MOB_ID} data component and drives the
 * card's name, colour, tooltip stats and foil effect.
 */
public class MobCardItem extends Item {

    public MobCardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stackOf(MobCard card) {
        ItemStack stack = new ItemStack(ModItems.MOB_CARD.get());
        stack.set(ModItems.MOB_ID.get(), card.id());
        return stack;
    }

    /** The card this stack represents, or null for a blank card. */
    public static MobCard cardOf(ItemStack stack) {
        return MobCards.byId(stack.get(ModItems.MOB_ID.get()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MobCard card = cardOf(stack);
        if (card != null && level.isClientSide) {
            ClientHooks.openCardScreen(card);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        MobCard card = cardOf(stack);
        if (card == null) {
            return super.getName(stack);
        }
        return Component.literal(card.displayName()).withStyle(tierColor(card.tier()));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        MobCard card = cardOf(stack);
        return card != null && card.tier() == Tier.LEGENDARY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        MobCard card = cardOf(stack);
        if (card == null) {
            tooltip.add(Component.literal("A blank mob card.").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.literal("★ " + card.tier().label() + " ★")
                .withStyle(tierColor(card.tier()), ChatFormatting.ITALIC));
        for (Stat stat : Stat.values()) {
            tooltip.add(Component.literal(stat.label + ": ").withStyle(statColor(stat))
                    .append(Component.literal(String.valueOf(card.stat(stat)))
                            .withStyle(ChatFormatting.WHITE)));
        }
        tooltip.add(Component.literal("Right-click to view the card").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    public static ChatFormatting tierColor(Tier tier) {
        return switch (tier) {
            case COMMON -> ChatFormatting.GRAY;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
        };
    }

    public static ChatFormatting statColor(Stat stat) {
        return switch (stat) {
            case HEALTH -> ChatFormatting.RED;
            case ATTACK -> ChatFormatting.GOLD;
            case SIZE -> ChatFormatting.LIGHT_PURPLE;
            case SPEED -> ChatFormatting.AQUA;
            case FARMABLE -> ChatFormatting.GREEN;
            case RARITY -> ChatFormatting.YELLOW;
        };
    }
}
