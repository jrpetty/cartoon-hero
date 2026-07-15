package com.gadgets;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.util.math.Direction;
import net.minecraft.item.ItemStack;

/** Moves items between two vanilla inventories, one at a time, respecting stacking and slot validity. */
public final class ItemTransfer {
    private ItemTransfer() {
    }

    public static boolean isEmpty(Inventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Move up to {@code max} items from {@code from} into {@code to}; returns the number moved. */
    public static int move(Inventory from, Inventory to, int max) {
        int moved = 0;
        for (int i = 0; i < from.size() && moved < max; i++) {
            ItemStack stack = from.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (from instanceof SidedInventory sided && !sided.canExtract(i, stack, Direction.DOWN)) {
                continue; // respect inventories that forbid automated extraction
            }
            while (!stack.isEmpty() && moved < max && insertOne(to, stack)) {
                stack.decrement(1);
                moved++;
            }
            from.setStack(i, stack);
        }
        if (moved > 0) {
            from.markDirty();
            to.markDirty();
        }
        return moved;
    }

    private static boolean insertOne(Inventory to, ItemStack src) {
        int size = to.size();
        for (int i = 0; i < size; i++) {
            ItemStack dst = to.getStack(i);
            if (!dst.isEmpty() && ItemStack.areItemsAndComponentsEqual(dst, src)
                    && dst.getCount() < Math.min(dst.getMaxCount(), to.getMaxCountPerStack())
                    && to.isValid(i, src)) {
                dst.increment(1);
                to.setStack(i, dst);
                return true;
            }
        }
        for (int i = 0; i < size; i++) {
            if (to.getStack(i).isEmpty() && to.isValid(i, src)) {
                to.setStack(i, src.copyWithCount(1));
                return true;
            }
        }
        return false;
    }
}
