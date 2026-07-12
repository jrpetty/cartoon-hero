package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Shared helpers for finding, removing and granting mob cards in an inventory. */
public final class CardActions {

    private CardActions() {
    }

    /** Count non-foil (or foil) cards of a given mob in the player's inventory. */
    public static int count(ServerPlayer player, String mobId, boolean foil) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard card = MobCardItem.cardOf(s);
            if (card != null && card.id().equals(mobId) && MobCardItem.isFoilCard(s) == foil) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Remove up to {@code amount} matching cards; returns how many were removed. */
    public static int remove(ServerPlayer player, String mobId, boolean foil, int amount) {
        int removed = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && removed < amount; i++) {
            ItemStack s = inv.getItem(i);
            MobCard card = MobCardItem.cardOf(s);
            if (card != null && card.id().equals(mobId) && MobCardItem.isFoilCard(s) == foil) {
                int take = Math.min(s.getCount(), amount - removed);
                s.shrink(take);
                removed += take;
            }
        }
        return removed;
    }

    /** Add a stack to the inventory, or drop it at the player's feet if full. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
