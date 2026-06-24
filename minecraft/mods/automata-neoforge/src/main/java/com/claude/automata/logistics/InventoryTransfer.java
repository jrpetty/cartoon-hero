package com.claude.automata.logistics;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helpers for moving items between arbitrary inventories (chests, barrels,
 * machines, hoppers…) the way a hopper does, but driven by the Logistics
 * Router. Respects {@link WorldlyContainer} so machine input/output faces work.
 */
public final class InventoryTransfer {
	private InventoryTransfer() {
	}

	/** The block-entity inventory at {@code pos}, or null if there isn't one. */
	public static Container inventoryAt(Level world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		return be instanceof Container inv ? inv : null;
	}

	private static int[] slotsFor(Container inv, Direction side) {
		if (inv instanceof WorldlyContainer sided) {
			return sided.getSlotsForFace(side);
		}
		int[] all = new int[inv.getContainerSize()];
		for (int i = 0; i < all.length; i++) {
			all[i] = i;
		}
		return all;
	}

	private static boolean canExtract(Container inv, int slot, ItemStack stack, Direction side) {
		return !(inv instanceof WorldlyContainer sided) || sided.canTakeItemThroughFace(slot, stack, side);
	}

	private static boolean canInsert(Container inv, int slot, ItemStack stack, Direction side) {
		if (!inv.canPlaceItem(slot, stack)) {
			return false;
		}
		return !(inv instanceof WorldlyContainer sided) || sided.canPlaceItemThroughFace(slot, stack, side);
	}

	/**
	 * Insert as much of {@code stack} as fits into {@code inv} (from {@code side}).
	 * Mutates and returns the leftover.
	 */
	public static ItemStack insert(Container inv, ItemStack stack, Direction side) {
		int[] slots = slotsFor(inv, side);
		// First merge into matching stacks, then fill empty slots.
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int slot : slots) {
				if (stack.isEmpty()) {
					break;
				}
				ItemStack cur = inv.getItem(slot);
				if (pass == 0) {
					if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, stack)
							&& canInsert(inv, slot, stack, side)) {
						int space = Math.min(inv.getMaxStackSize(), cur.getMaxStackSize()) - cur.getCount();
						int move = Math.min(space, stack.getCount());
						if (move > 0) {
							cur.grow(move);
							stack.shrink(move);
							inv.setChanged();
						}
					}
				} else if (cur.isEmpty() && canInsert(inv, slot, stack, side)) {
					int move = Math.min(stack.getMaxStackSize(), stack.getCount());
					inv.setItem(slot, stack.copyWithCount(move));
					stack.shrink(move);
					inv.setChanged();
				}
			}
		}
		return stack;
	}

	/**
	 * Pull up to {@code max} items from {@code source} (accessed from
	 * {@code side}) matching {@code filter}, but only as many as {@code dest}
	 * inventories can actually accept. Returns the count moved.
	 *
	 * @param destinations the remote inventories to fill, tried round-robin from {@code startIndex}
	 */
	public static int pullAndDistribute(Container source, Direction side, Predicate<ItemStack> filter,
			int max, Container[] destinations, int startIndex) {
		int[] slots = slotsFor(source, side);
		for (int slot : slots) {
			ItemStack stack = source.getItem(slot);
			if (stack.isEmpty() || !filter.test(stack) || !canExtract(source, slot, stack, side)) {
				continue;
			}
			int take = Math.min(max, stack.getCount());
			ItemStack moving = stack.copyWithCount(take);
			int before = moving.getCount();

			for (int i = 0; i < destinations.length && !moving.isEmpty(); i++) {
				Container dest = destinations[(startIndex + i) % destinations.length];
				if (dest != null && dest != source) {
					moving = insert(dest, moving, Direction.UP);
				}
			}

			int moved = before - moving.getCount();
			if (moved > 0) {
				source.removeItem(slot, moved);
				source.setChanged();
				return moved;
			}
		}
		return 0;
	}
}
