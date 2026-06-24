package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Mass Storage Drawer — holds a huge stack of a single item type. It binds
 * to the first item it receives and stores up to {@value #CAPACITY} of it.
 *
 * <p>To stay compatible with hoppers, it exposes a normal 64-cap input buffer
 * (top/sides) and output buffer (bottom); each tick it pulls the input buffer
 * into its big internal count and tops the output buffer back up. Right-click
 * for a readout, with an item to deposit, or empty-handed to withdraw a stack.
 */
public class DrawerBlockEntity extends MachineBlockEntity {
	public static final int CAPACITY = 4096;
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int[] IN = {INPUT_SLOT};
	private static final int[] OUT = {OUTPUT_SLOT};

	private Item bound = Items.AIR;
	private int stored = 0;

	public DrawerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DRAWER, pos, state, 2);
	}

	@Override
	protected int[] inputSlots() {
		return IN;
	}

	@Override
	protected int outputSlot() {
		return OUTPUT_SLOT;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return bound == Items.AIR || stack.isOf(bound);
	}

	@Override
	protected int maxProgress() {
		return 1;
	}

	public Item getBound() {
		return bound;
	}

	/** Total held including both buffers. */
	public int totalCount() {
		int total = stored;
		if (inventory.get(INPUT_SLOT).isOf(bound)) {
			total += inventory.get(INPUT_SLOT).getCount();
		}
		if (inventory.get(OUTPUT_SLOT).isOf(bound)) {
			total += inventory.get(OUTPUT_SLOT).getCount();
		}
		return bound == Items.AIR ? 0 : total;
	}

	/** Add up to {@code stack}'s worth; mutates and returns leftover. */
	public ItemStack deposit(ItemStack stack) {
		if (stack.isEmpty()) {
			return stack;
		}
		if (bound == Items.AIR) {
			bound = stack.getItem();
		}
		if (!stack.isOf(bound)) {
			return stack;
		}
		int room = CAPACITY - stored;
		int amount = Math.min(room, stack.getCount());
		if (amount > 0) {
			stored += amount;
			stack.decrement(amount);
			markDirty();
		}
		return stack;
	}

	/** Remove up to {@code max} of the stored item for the player. */
	public ItemStack withdraw(int max) {
		if (bound == Items.AIR) {
			return ItemStack.EMPTY;
		}
		int amount = Math.min(max, stored);
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		stored -= amount;
		ItemStack out = new ItemStack(bound, amount);
		markDirty();
		return out;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		boolean dirty = false;

		// Pull the input buffer into the big internal count.
		ItemStack in = inventory.get(INPUT_SLOT);
		if (!in.isEmpty()) {
			if (bound == Items.AIR) {
				bound = in.getItem();
			}
			if (in.isOf(bound) && stored < CAPACITY) {
				int amount = Math.min(in.getCount(), CAPACITY - stored);
				stored += amount;
				in.decrement(amount);
				dirty = true;
			}
		}

		// Top up the output buffer from the internal count.
		if (bound != Items.AIR && stored > 0) {
			ItemStack out = inventory.get(OUTPUT_SLOT);
			if (out.isEmpty()) {
				int amount = Math.min(stored, bound.getDefaultStack().getMaxCount());
				inventory.set(OUTPUT_SLOT, new ItemStack(bound, amount));
				stored -= amount;
				dirty = true;
			} else if (out.isOf(bound) && out.getCount() < out.getMaxCount()) {
				int amount = Math.min(stored, out.getMaxCount() - out.getCount());
				out.increment(amount);
				stored -= amount;
				dirty = true;
			}
		}

		// Forget the bound item once completely empty, so the drawer can be reused.
		if (stored == 0 && inventory.get(INPUT_SLOT).isEmpty() && inventory.get(OUTPUT_SLOT).isEmpty()
				&& bound != Items.AIR) {
			bound = Items.AIR;
			dirty = true;
		}

		if (dirty) {
			markDirty();
		}
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return side == Direction.DOWN ? OUT : IN;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return slot == INPUT_SLOT && isValidInput(stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("Bound", Registries.ITEM.getId(bound).toString());
		nbt.putInt("Stored", stored);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		Identifier id = Identifier.tryParse(nbt.getString("Bound"));
		bound = (id != null && Registries.ITEM.containsId(id)) ? Registries.ITEM.get(id) : Items.AIR;
		stored = nbt.getInt("Stored");
	}
}
