package com.gadgets;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/** Moves items between two vanilla containers, one at a time, respecting stacking and slot validity. */
public final class ItemTransfer {
    private ItemTransfer() {
    }

    public static boolean isEmpty(Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The stack {@link #move} would draw from next, or empty. Returned as a
     * live reference for cheapness — copy it if you mean to keep it, because
     * the next move will shrink it.
     */
    public static ItemStack peek(Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (inv instanceof WorldlyContainer sided && !sided.canTakeItemThroughFace(i, stack, Direction.DOWN)) {
                continue; // the same slots move() is allowed to touch
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Move up to {@code max} items from {@code from} into {@code to}; returns the number moved. */
    public static int move(Container from, Container to, int max) {
        int moved = 0;
        for (int i = 0; i < from.getContainerSize() && moved < max; i++) {
            ItemStack stack = from.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (from instanceof WorldlyContainer sided && !sided.canTakeItemThroughFace(i, stack, Direction.DOWN)) {
                continue; // respect containers that forbid automated extraction
            }
            while (!stack.isEmpty() && moved < max && insertOne(to, stack)) {
                stack.shrink(1);
                moved++;
            }
            from.setItem(i, stack);
        }
        if (moved > 0) {
            from.setChanged();
            to.setChanged();
        }
        return moved;
    }

    private static boolean insertOne(Container to, ItemStack src) {
        int size = to.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack dst = to.getItem(i);
            if (!dst.isEmpty() && ItemStack.isSameItemSameComponents(dst, src)
                    && dst.getCount() < Math.min(dst.getMaxStackSize(), to.getMaxStackSize())
                    && to.canPlaceItem(i, src)) {
                dst.grow(1);
                to.setItem(i, dst);
                return true;
            }
        }
        for (int i = 0; i < size; i++) {
            if (to.getItem(i).isEmpty() && to.canPlaceItem(i, src)) {
                to.setItem(i, src.copyWithCount(1));
                return true;
            }
        }
        return false;
    }
}
