package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A booster pack: right-click to rip it open and pull five distinct mob
 * cards. Pulls are weighted by spawn rarity, so legendaries are rare.
 */
public class CardPackItem extends Item {

    public static final int CARDS_PER_PACK = 5;
    /** Chance for any given pull to come out as a holographic foil. */
    public static final float FOIL_CHANCE = 0.09F;

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

        var random = ThreadLocalRandom.current();
        List<MobCard> pulls = MobCards.openPack(CARDS_PER_PACK, random);
        List<PackOpenedPayload.Pull> results = new ArrayList<>();
        boolean anyFoil = false;

        for (MobCard card : pulls) {
            boolean foil = random.nextFloat() < FOIL_CHANCE;
            anyFoil |= foil;
            ItemStack cardStack = MobCardItem.stackOf(card, foil);
            if (!player.getInventory().add(cardStack)) {
                player.drop(cardStack, false);
            }
            boolean newlyCollected = player instanceof ServerPlayer serverPlayer
                    && CollectionTracker.record(serverPlayer, card.id(), foil);
            results.add(new PackOpenedPayload.Pull(card.id(), foil, newlyCollected));
        }

        if (player instanceof ServerPlayer serverPlayer) {
            // drive the animated reveal screen on the opener's client
            PacketDistributor.sendToPlayer(serverPlayer, new PackOpenedPayload(results));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                anyFoil ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.6F, anyFoil ? 1.0F : 1.4F);
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
