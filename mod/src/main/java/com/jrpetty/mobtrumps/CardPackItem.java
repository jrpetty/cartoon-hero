package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A booster pack: right-click to rip it open and pull five distinct mob
 * cards. Pulls are weighted by spawn rarity, so legendaries are rare.
 */
public class CardPackItem extends Item {

    public static final int CARDS_PER_PACK = 5;

    public CardPackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(pack, true);
        }

        if (!player.getAbilities().instabuild) {
            pack.shrink(1);
        }

        List<MobCard> pulls = MobCards.openPack(CARDS_PER_PACK, ThreadLocalRandom.current());
        player.displayClientMessage(Component.literal("You rip open a card pack...")
                .withStyle(ChatFormatting.YELLOW), false);
        for (MobCard card : pulls) {
            ItemStack cardStack = MobCardItem.stackOf(card);
            if (!player.getInventory().add(cardStack)) {
                player.drop(cardStack, false);
            }
            player.displayClientMessage(Component.literal("  + ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(card.displayName())
                            .withStyle(MobCardItem.tierColor(card.tier())))
                    .append(Component.literal(" [" + card.tier().label() + "]")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        return InteractionResultHolder.sidedSuccess(pack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to pull " + CARDS_PER_PACK + " mob cards.")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
