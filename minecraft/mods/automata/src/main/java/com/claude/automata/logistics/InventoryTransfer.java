package com.claude.automata.logistics;

import java.util.function.Predicate;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Helpers for moving items between arbitrary inventories (chests, barrels,
 * machines, hoppers…) the way a hopper does, but driven by the Logistics
 * Router. Respects {@link SidedInventory} so machine input/output faces work.
 */
public final class InventoryTransfer {
	private InventoryTransfer() {
	}

	/** The block-entity inventory at {@code pos}, or null if there isn't one. */
	public static Inventory inventoryAt(World world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		return be instanceof Inventory inv ? inv : null;
	}

	private static int[] slotsFor(Inventory inv, Direction side) {
		if (inv instanceof SidedInventory sided) {
			return sided.getAvailableSlots(side);
		}
		int[] all = new int[inv.size()];
		for (int i = 0; i < all.length; i++) {
			all[i] = i;
		}
		return all;
	}

	private static boolean canExtract(Inventory inv, int slot, ItemStack stack, Direction side) {
		return !(inv instanceof SidedInventory sided) || sided.canExtract(slot, stack, side);
	}

	private static boolean canInsert(Inventory inv, int slot, ItemStack stack, Direction side) {
		if (!inv.isValid(slot, stack)) {
			return false;
		}
		return !(inv instanceof SidedInventory sided) || sided.canInsert(slot, stack, side);
	}

	/**
	 * Insert as much of {@code stack} as fits into {@code inv} (from {@code side}).
	 * Mutates and returns the leftover.
	 */
	public static ItemStack insert(Inventory inv, ItemStack stack, Direction side) {
		int[] slots = slotsFor(inv, side);
		// First merge into matching stacks, then fill empty slots.
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int slot : slots) {
				if (stack.isEmpty()) {
					break;
				}
				ItemStack cur = inv.getStack(slot);
				if (pass == 0) {
					if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, stack)
							&& canInsert(inv, slot, stack, side)) {
						int space = Math.min(inv.getMaxCountPerStack(), cur.getMaxCount()) - cur.getCount();
						int move = Math.min(space, stack.getCount());
						if (move > 0) {
							cur.increment(move);
							stack.decrement(move);
							inv.markDirty();
						}
					}
				} else if (cur.isEmpty() && canInsert(inv, slot, stack, side)) {
					int move = Math.min(stack.getMaxCount(), stack.getCount());
					inv.setStack(slot, stack.copyWithCount(move));
					stack.decrement(move);
					inv.markDirty();
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
	public static int pullAndDistribute(Inventory source, Direction side, Predicate<ItemStack> filter,
			int max, Inventory[] destinations, int startIndex) {
		int[] slots = slotsFor(source, side);
		for (int slot : slots) {
			ItemStack stack = source.getStack(slot);
			if (stack.isEmpty() || !filter.test(stack) || !canExtract(source, slot, stack, side)) {
				continue;
			}
			int take = Math.min(max, stack.getCount());
			ItemStack moving = stack.copyWithCount(take);
			int before = moving.getCount();

			for (int i = 0; i < destinations.length && !moving.isEmpty(); i++) {
				Inventory dest = destinations[(startIndex + i) % destinations.length];
				if (dest != null && dest != source) {
					moving = insert(dest, moving, Direction.UP);
				}
			}

			int moved = before - moving.getCount();
			if (moved > 0) {
				source.removeStack(slot, moved);
				source.markDirty();
				return moved;
			}
		}
		return 0;
	}
}
