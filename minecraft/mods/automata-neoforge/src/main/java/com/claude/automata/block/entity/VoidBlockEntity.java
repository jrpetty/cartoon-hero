package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Void Hatch — accepts items from any side (hopper or hand) and destroys
 * them, so overflow never backs a line up. It has one slot that it empties
 * every tick; nothing can be extracted.
 */
public class VoidBlockEntity extends MachineBlockEntity {
	private static final int[] SLOTS = {0};

	public VoidBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.VOID.get(), pos, state, 1);
	}

	@Override
	protected int[] inputSlots() {
		return SLOTS;
	}

	@Override
	protected int outputSlot() {
		return 0;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return true;
	}

	@Override
	protected int maxProgress() {
		return 1;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return true;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (!inventory.get(0).isEmpty()) {
			inventory.set(0, ItemStack.EMPTY);
		}
	}
}
