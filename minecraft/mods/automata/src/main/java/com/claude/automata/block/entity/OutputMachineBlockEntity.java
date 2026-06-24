package com.claude.automata.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Base class for machines that <em>produce</em> items into the world rather than
 * consuming them — the Auto-Miner and Item Collector. Every slot is an output:
 * hoppers (on any side) may pull from them, but nothing can be inserted. This is
 * the bridge between world-gathering machines and the rest of the factory.
 */
public abstract class OutputMachineBlockEntity extends MachineBlockEntity {
	private static final int[] NO_INPUTS = new int[0];
	private final int[] allSlots;

	protected OutputMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
		super(type, pos, state, size);
		this.allSlots = new int[size];
		for (int i = 0; i < size; i++) {
			this.allSlots[i] = i;
		}
	}

	@Override
	protected int[] inputSlots() {
		return NO_INPUTS;
	}

	@Override
	protected int outputSlot() {
		return 0;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return false;
	}

	@Override
	protected int maxProgress() {
		return 1;
	}

	// Every slot is extract-only, reachable from any side.
	@Override
	public int[] getAvailableSlots(Direction side) {
		return allSlots;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return true;
	}

	/** True if any slot is empty (used as a simple "has room" back-pressure check). */
	protected boolean hasEmptySlot() {
		for (int i = 0; i < size(); i++) {
			if (inventory.get(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Merge {@code stack} into the output slots. Returns whatever could not fit
	 * (an empty stack if everything was stored). Mutates the passed stack.
	 */
	protected ItemStack addOutput(ItemStack stack) {
		for (int i = 0; i < size() && !stack.isEmpty(); i++) {
			ItemStack cur = inventory.get(i);
			if (cur.isEmpty()) {
				inventory.set(i, stack.copyWithCount(stack.getCount()));
				stack.setCount(0);
			} else if (ItemStack.areItemsAndComponentsEqual(cur, stack)) {
				int space = cur.getMaxCount() - cur.getCount();
				int move = Math.min(space, stack.getCount());
				cur.increment(move);
				stack.decrement(move);
			}
		}
		return stack;
	}

	@Override
	public void extractToPlayer(PlayerEntity player) {
		for (int i = 0; i < size(); i++) {
			ItemStack stack = inventory.get(i);
			if (!stack.isEmpty()) {
				player.getInventory().offerOrDrop(stack);
				inventory.set(i, ItemStack.EMPTY);
				markDirty();
				return;
			}
		}
	}
}
