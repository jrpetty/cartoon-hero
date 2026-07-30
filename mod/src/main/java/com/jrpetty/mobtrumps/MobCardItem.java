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
        return stackOf(card, false);
    }

    public static ItemStack stackOf(MobCard card, boolean foil) {
        ItemStack stack = new ItemStack(ModItems.MOB_CARD.get());
        stack.set(ModItems.MOB_ID.get(), card.id());
        if (foil) {
            stack.set(ModItems.FOIL.get(), true);
        }
        return stack;
    }

    /**
     * A brand new card, stamped with the next serial for its mob and credited
     * to whoever earned it. This is the only way an authentic card is born.
     */
    public static ItemStack issued(net.minecraft.server.level.ServerPlayer unlocker,
                                   MobCard card, boolean foil) {
        ItemStack stack = stackOf(card, foil);
        CardIdentityService.issue(unlocker == null ? null : unlocker.getServer(), stack, unlocker,
                foil ? com.jrpetty.mobtrumps.game.CardEdition.FOIL
                     : com.jrpetty.mobtrumps.game.CardEdition.STANDARD);
        return stack;
    }

    /** The card this stack represents, or null for a blank card. */
    public static MobCard cardOf(ItemStack stack) {
        return MobCards.byId(stack.get(ModItems.MOB_ID.get()));
    }

    /** Whether this card is a rare holographic foil variant. */
    public static boolean isFoilCard(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModItems.FOIL.get()));
    }

    /**
     * Right-click opens the inspection screen. Two exceptions, both about the
     * sleeve: sneaking pops a sleeved card out of its sleeve, and a sleeve held
     * in the off hand slips the card in. Inspecting never wears a card — the
     * card is already in hand, so no hand ENTRY has happened.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MobCard card = cardOf(stack);
        if (card == null) {
            return InteractionResultHolder.pass(stack);
        }
        boolean sleeved = CardIdentityService.isSleeved(stack);
        ItemStack offhand = player.getOffhandItem();
        boolean hasSleeve = offhand.is(ModItems.CARD_SLEEVE.get());

        if (player.isShiftKeyDown() && sleeved) {
            if (!level.isClientSide) {
                unsleeve(player, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!sleeved && hasSleeve) {
            if (!level.isClientSide) {
                sleeve(player, stack, offhand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (level.isClientSide) {
            ClientHooks.openCardInspect(hand);
        } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // a card that predates the serial system joins it on first inspection
            CardIdentityService.ensureRegistered(serverPlayer.getServer(), stack);
            // inspecting a card registers it in the collection book
            CollectionTracker.record(serverPlayer, card.id(), isFoilCard(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Slip the held card into a sleeve from the off hand, consuming the sleeve. */
    private static void sleeve(Player player, ItemStack stack, ItemStack sleeveStack) {
        CardWear wear = CardIdentityService.wearOf(stack);
        if (wear.sleeved()) {
            return;
        }
        CardIdentityService.setWear(stack, wear.withSleeved(true));
        sleeveStack.shrink(1);
        player.displayClientMessage(Component.literal("Sleeved at " + wear.condition() + "% "
                        + wear.label() + " — it will not wear again.")
                .withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    /** Take the card back out; the sleeve returns intact, the card unrepaired. */
    private static void unsleeve(Player player, ItemStack stack) {
        CardWear wear = CardIdentityService.wearOf(stack);
        if (!wear.sleeved()) {
            return;
        }
        CardIdentityService.setWear(stack, wear.withSleeved(false));
        ItemStack sleeve = new ItemStack(ModItems.CARD_SLEEVE.get());
        if (!player.getInventory().add(sleeve)) {
            player.drop(sleeve, false);
        }
        player.displayClientMessage(Component.literal("Unsleeved — exposed again at "
                        + wear.condition() + "%.").withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public Component getName(ItemStack stack) {
        MobCard card = cardOf(stack);
        if (card == null) {
            return super.getName(stack);
        }
        Component name = Component.literal(card.displayName()).withStyle(tierColor(card.tier()));
        if (isFoilCard(stack)) {
            return Component.literal("✦ ").withStyle(ChatFormatting.WHITE)
                    .append(name)
                    .append(Component.literal(" ✦").withStyle(ChatFormatting.WHITE));
        }
        return name;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        MobCard card = cardOf(stack);
        return isFoilCard(stack) || (card != null && card.tier() == Tier.LEGENDARY);
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
        // identity: serial, condition and protection, at a glance
        CardIdentity identity = CardIdentityService.identityOf(stack);
        if (identity != null && identity.valid()) {
            CardWear wear = CardIdentityService.wearOf(stack);
            tooltip.add(Component.literal(CardIdentityService.serialText(stack))
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(wear.condition() + "%  " + wear.label())
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(
                            net.minecraft.network.chat.TextColor.fromRgb(
                                    com.jrpetty.mobtrumps.game.CardCondition.color(wear.condition())
                                            & 0xFFFFFF)))
                    .append(wear.sleeved()
                            ? Component.literal("   ▣ Sleeved").withStyle(ChatFormatting.AQUA)
                            : Component.empty()));
        }
        if (card.category() != null) {
            tooltip.add(Component.literal(card.category().label()).withStyle(
                    net.minecraft.network.chat.Style.EMPTY.withColor(
                            net.minecraft.network.chat.TextColor.fromRgb(card.category().accent() & 0xFFFFFF))));
        }
        boolean foil = isFoilCard(stack);
        // a holographic card shows its boosted stats; +N marks the lift
        MobCard shown = foil ? card.foilVersion() : card;
        if (foil) {
            tooltip.add(Component.literal("✦ Holographic Foil ✦")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        }
        for (Stat stat : Stat.values()) {
            int value = shown.stat(stat);
            int gain = value - card.stat(stat);
            Component line = Component.literal(stat.label + ": ").withStyle(statColor(stat))
                    .append(Component.literal(String.valueOf(value))
                            .withStyle(gain > 0 ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            if (gain > 0) {
                line = line.copy().append(Component.literal(" (+" + gain + ")")
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
            if (stat.lowerWins) {
                // Rarity is the odd one out: 1 is a legendary mob, and it beats a 10
                line = line.copy().append(Component.literal("  ▼ lower wins")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(line);
        }
        tooltip.add(Component.literal("Right-click to inspect · flip for its history")
                .withStyle(ChatFormatting.DARK_GRAY));
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
