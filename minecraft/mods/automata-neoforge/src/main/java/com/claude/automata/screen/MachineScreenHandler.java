package com.claude.automata.screen;

import com.claude.automata.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared container for the single-input / single-output processing machines.
 * Slot 0 is the input, slot 1 is the (extract-only) output, and a two-value
 * {@link ContainerData} syncs the progress for the on-screen arrow.
 *
 * <p>The same logic is used on both sides: the server passes the machine block
 * entity directly, while the client receives the machine's position as extended
 * screen data and resolves the block entity from the world.
 */
public class MachineScreenHandler extends AbstractContainerMenu {
	private final Container inventory;
	private final ContainerData propertyDelegate;

	/** Client constructor: resolves the machine from the BlockPos sent as extended data. */
	public MachineScreenHandler(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(syncId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
	}

	/** Server constructor: opened with the machine block entity directly. */
	public MachineScreenHandler(int syncId, Inventory playerInventory, MachineBlockEntity machine) {
		this(syncId, playerInventory, (Container) machine);
	}

	private MachineScreenHandler(int syncId, Inventory playerInventory, Container resolved) {
		super(ModScreenHandlers.MACHINE.get(), syncId);

		if (resolved instanceof MachineBlockEntity machine) {
			this.inventory = machine;
			this.propertyDelegate = machine.getPropertyDelegate();
		} else {
			this.inventory = new SimpleContainer(2);
			this.propertyDelegate = new SimpleContainerData(2);
		}

		this.inventory.startOpen(playerInventory.player);
		addDataSlots(this.propertyDelegate);

		// Machine slots: input (left) and output (right).
		addSlot(new Slot(this.inventory, 0, 56, 35));
		addSlot(new Slot(this.inventory, 1, 116, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
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

	/** Resolve the machine inventory client-side from its position, falling back to a dummy. */
	private static Container resolve(Inventory playerInventory, BlockPos pos) {
		BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
		if (be instanceof MachineBlockEntity machine) {
			return machine;
		}
		return new SimpleContainer(2);
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
	public boolean stillValid(Player player) {
		return inventory.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack original = slot.getItem();
			moved = original.copy();
			int machineSlots = this.inventory.getContainerSize();
			if (index < machineSlots) {
				// From the machine into the player inventory.
				if (!moveItemStackTo(original, machineSlots, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(original, 0, 1, false)) {
				// From the player into the input slot only.
				return ItemStack.EMPTY;
			}

			if (original.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return moved;
	}
}
