package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Recycler;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Card Shredder and the Printing Press: pulp spares, print new cards.
 *
 * <p>Only <b>spares</b> can be shredded, and "spare" now has a precise meaning:
 * the mob is already filed in your Collection Book. Since v1.59.0 the book is
 * the record of what you own, so a loose card whose mob is filed is by
 * definition a second copy, and pulping it cannot cost you collection progress
 * or a card the campaign needs. A card of a mob you have <em>not</em> filed is
 * your only one, and the machine refuses it.
 */
public final class RecyclerManager {

    public static final int MODE_SHREDDER = 0;
    public static final int MODE_PRESS = 1;

    private RecyclerManager() {
    }

    public static void open(ServerPlayer player, int mode) {
        PacketDistributor.sendToPlayer(player, new RecyclerMenuPayload(mode));
    }

    // --- fragments ----------------------------------------------------------

    public static int fragments(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.CARD_FRAGMENT.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Take exactly {@code want} fragments, or none at all. */
    private static boolean takeFragments(ServerPlayer player, int want) {
        if (want <= 0 || fragments(player) < want) {
            return false;
        }
        int left = want;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.CARD_FRAGMENT.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return left == 0;
    }

    private static void giveFragments(ServerPlayer player, int count) {
        int left = count;
        while (left > 0) {
            int stack = Math.min(64, left);
            CardActions.give(player, new ItemStack(ModItems.CARD_FRAGMENT.get(), stack));
            left -= stack;
        }
    }

    // --- shredding ----------------------------------------------------------

    /** Whether this exact stack may be fed to the shredder. */
    public static boolean isSpare(ServerPlayer player, ItemStack stack) {
        MobCard card = MobCardItem.cardOf(stack);
        if (card == null) {
            return false;
        }
        return BinderStorage.isStored(player, card.id(), MobCardItem.isFoilCard(stack));
    }

    /** Shred one loose spare of this mob. Returns the fragments paid, or 0. */
    public static int shredOne(ServerPlayer player, String mobId, boolean foil) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard card = MobCardItem.cardOf(s);
            if (card == null || !card.id().equals(mobId)) continue;
            if (MobCardItem.isFoilCard(s) != foil) continue;
            if (!isSpare(player, s)) continue;

            int paid = Recycler.yield(card.tier(), CardIdentityService.wearOf(s).condition());
            s.shrink(1);
            giveFragments(player, paid);
            grind(player);
            result(player, RecyclerResultPayload.SHRED, mobId, paid, 1);
            player.sendSystemMessage(Component.literal("Shredded " + (foil ? "a foil " : "")
                            + card.displayName() + " — " + paid + " fragment"
                            + (paid == 1 ? "" : "s") + ".")
                    .withStyle(ChatFormatting.GRAY));
            sync(player);
            return paid;
        }
        player.sendSystemMessage(Component.literal(
                        "The shredder will not take your only copy — file one in your book first.")
                .withStyle(ChatFormatting.RED));
        result(player, RecyclerResultPayload.REFUSED, mobId, 0, 0);
        return 0;
    }

    /** Shred every loose spare in the inventory in one pass. */
    public static int shredAll(ServerPlayer player) {
        var inv = player.getInventory();
        int paid = 0;
        int cards = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard card = MobCardItem.cardOf(s);
            if (card == null || !isSpare(player, s)) continue;
            paid += Recycler.yield(card.tier(), CardIdentityService.wearOf(s).condition());
            s.shrink(1);
            cards++;
        }
        if (cards == 0) {
            player.sendSystemMessage(Component.literal(
                            "Nothing to shred — every card you're carrying is one you still need.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        giveFragments(player, paid);
        grind(player);
        result(player, RecyclerResultPayload.SHRED_ALL, "", paid, cards);
        player.sendSystemMessage(Component.literal("Shredded " + cards + " spare"
                        + (cards == 1 ? "" : "s") + " — " + paid + " fragments.")
                .withStyle(ChatFormatting.GREEN));
        sync(player);
        return paid;
    }

    // --- printing -----------------------------------------------------------

    /**
     * Spend a stake on a print. Linear odds, and a failure keeps nothing —
     * that is what makes the expected cost identical at every stake, so the
     * choice is about variance rather than about finding the best number.
     */
    public static void print(ServerPlayer player, int tierOrdinal, int stake) {
        Tier[] tiers = Tier.values();
        if (tierOrdinal < 0 || tierOrdinal >= tiers.length) {
            return;
        }
        Tier tier = tiers[tierOrdinal];
        int bet = Recycler.clampStake(tier, stake);
        if (!takeFragments(player, bet)) {
            player.sendSystemMessage(Component.literal("Not enough fragments — that print needs "
                            + bet + ".").withStyle(ChatFormatting.RED));
            return;
        }
        boolean hit = player.getRandom().nextFloat() < Recycler.chance(tier, bet);
        if (!hit) {
            player.sendSystemMessage(Component.literal("MISPRINT — the press ruins the sheet. "
                            + bet + " fragments gone.")
                    .withStyle(ChatFormatting.RED));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 0.7F);
            result(player, RecyclerResultPayload.PRINT_MISS, "", bet, 0);
            sync(player);
            return;
        }
        List<MobCard> pool = new ArrayList<>();
        for (MobCard card : MobCards.ALL) {
            if (card.tier() == tier) {
                pool.add(card);
            }
        }
        if (pool.isEmpty()) {
            giveFragments(player, bet); // nothing to print; do not eat the stake
            sync(player);
            return;
        }
        MobCard printed = pool.get(player.getRandom().nextInt(pool.size()));
        CardActions.give(player, MobCardItem.issued(player, printed,
                com.jrpetty.mobtrumps.game.CardEdition.STANDARD));
        player.sendSystemMessage(Component.literal("Printed ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(printed.displayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.4F);
        result(player, RecyclerResultPayload.PRINT_HIT, printed.id(), bet, 1);
        sync(player);
    }

    /** Tell the screen what happened so it can play it out. */
    private static void result(ServerPlayer player, int kind, String mobId, int amount, int count) {
        PacketDistributor.sendToPlayer(player,
                new RecyclerResultPayload(kind, mobId == null ? "" : mobId, amount, count));
    }

    private static void grind(ServerPlayer player) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, 1.0F);
    }

    /** Push the fragment count back so the screen's numbers stay honest. */
    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RecyclerSyncPayload(fragments(player)));
    }
}
