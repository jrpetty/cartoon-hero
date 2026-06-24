package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Void Hatch — accepts items from any side (hopper or hand) and destroys
 * them, so overflow never backs a line up. It has one slot that it empties
 * every tick; nothing can be extracted.
 */
public class VoidBlockEntity extends MachineBlockEntity {
	private static final int[] SLOTS = {0};

	public VoidBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.VOID, pos, state, 1);
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
	public int[] getAvailableSlots(Direction side) {
		return SLOTS;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return true;
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (!inventory.get(0).isEmpty()) {
			inventory.set(0, ItemStack.EMPTY);
		}
	}
}
