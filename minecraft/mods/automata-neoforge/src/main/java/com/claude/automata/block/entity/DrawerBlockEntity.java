package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
		super(ModBlockEntities.DRAWER.get(), pos, state, 2);
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
		return bound == Items.AIR || stack.is(bound);
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
		if (inventory.get(INPUT_SLOT).is(bound)) {
			total += inventory.get(INPUT_SLOT).getCount();
		}
		if (inventory.get(OUTPUT_SLOT).is(bound)) {
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
		if (!stack.is(bound)) {
			return stack;
		}
		int room = CAPACITY - stored;
		int amount = Math.min(room, stack.getCount());
		if (amount > 0) {
			stored += amount;
			stack.shrink(amount);
			setChanged();
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
		setChanged();
		return out;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		boolean dirty = false;

		// Pull the input buffer into the big internal count.
		ItemStack in = inventory.get(INPUT_SLOT);
		if (!in.isEmpty()) {
			if (bound == Items.AIR) {
				bound = in.getItem();
			}
			if (in.is(bound) && stored < CAPACITY) {
				int amount = Math.min(in.getCount(), CAPACITY - stored);
				stored += amount;
				in.shrink(amount);
				dirty = true;
			}
		}

		// Top up the output buffer from the internal count.
		if (bound != Items.AIR && stored > 0) {
			ItemStack out = inventory.get(OUTPUT_SLOT);
			if (out.isEmpty()) {
				int amount = Math.min(stored, bound.getDefaultInstance().getMaxStackSize());
				inventory.set(OUTPUT_SLOT, new ItemStack(bound, amount));
				stored -= amount;
				dirty = true;
			} else if (out.is(bound) && out.getCount() < out.getMaxStackSize()) {
				int amount = Math.min(stored, out.getMaxStackSize() - out.getCount());
				out.grow(amount);
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
			setChanged();
		}
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? OUT : IN;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == INPUT_SLOT && isValidInput(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putString("Bound", BuiltInRegistries.ITEM.getKey(bound).toString());
		nbt.putInt("Stored", stored);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		ResourceLocation id = ResourceLocation.tryParse(nbt.getString("Bound"));
		bound = (id != null && BuiltInRegistries.ITEM.containsKey(id)) ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
		stored = nbt.getInt("Stored");
	}
}
