package com.claude.automata.screen;

import com.claude.automata.block.entity.MachineBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Shared container for the single-input / single-output processing machines.
 * Slot 0 is the input, slot 1 is the (extract-only) output, and a two-value
 * {@link PropertyDelegate} syncs the progress for the on-screen arrow.
 *
 * <p>The same constructor is used on both sides: the server passes the machine's
 * position and the client receives it as extended screen data, then both resolve
 * the block entity from the world.
 */
public class MachineScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final PropertyDelegate propertyDelegate;

	public MachineScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		super(ModScreenHandlers.MACHINE, syncId);

		BlockEntity be = playerInventory.player.getWorld().getBlockEntity(pos);
		if (be instanceof MachineBlockEntity machine) {
			this.inventory = machine;
			this.propertyDelegate = machine.getPropertyDelegate();
		} else {
			this.inventory = new SimpleInventory(2);
			this.propertyDelegate = new ArrayPropertyDelegate(2);
		}

		this.inventory.onOpen(playerInventory.player);
		addProperties(this.propertyDelegate);

		// Machine slots: input (left) and output (right).
		addSlot(new Slot(this.inventory, 0, 56, 35));
		addSlot(new Slot(this.inventory, 1, 116, 35) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return false;
			}
		});

		// Player inventory + hotbar.
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	/** Progress filled, scaled to the 24px arrow on the GUI. */
	public int getScaledProgress() {
		int progress = propertyDelegate.get(0);
		int max = propertyDelegate.get(1);
		if (max == 0 || progress == 0) {
			return 0;
		}
		return Math.min(24, progress * 24 / max);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack original = slot.getStack();
			moved = original.copy();
			int machineSlots = this.inventory.size();
			if (index < machineSlots) {
				// From the machine into the player inventory.
				if (!insertItem(original, machineSlots, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!insertItem(original, 0, 1, false)) {
				// From the player into the input slot only.
				return ItemStack.EMPTY;
			}

			if (original.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return moved;
	}
}
