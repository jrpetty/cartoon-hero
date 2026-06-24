package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Item Sorter — one input, two outputs. Items fed into the top are split:
 * anything in its sort list leaves the <em>bottom</em> face (sorted output),
 * and everything else leaves the <em>side</em> faces (reject output), so you
 * can hopper the two streams in different directions.
 *
 * <p>Configure the sort list by right-clicking the block with the item(s) you
 * want it to keep. Runs slowly by hand, faster with power.
 *
 * <p>Slot layout: 0 = input (top), 1–4 = sorted output (bottom), 5–8 = reject
 * output (sides).
 */
public class SorterBlockEntity extends MachineBlockEntity {
	private static final int[] INPUT = {0};
	private static final int[] SORTED = {1, 2, 3, 4};
	private static final int[] REJECT = {5, 6, 7, 8};
	private static final int ENERGY_PER_OP = 10;
	private static final int UNPOWERED_INTERVAL = 20;
	private static final int POWERED_INTERVAL = 4;
	private static final int BATCH_POWERED = 16;
	private static final int BATCH_UNPOWERED = 4;

	private final Set<Item> sortList = new LinkedHashSet<>();

	public SorterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SORTER.get(), pos, state, 9);
	}

	@Override
	protected int[] inputSlots() {
		return INPUT;
	}

	@Override
	protected int outputSlot() {
		return SORTED[0];
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return true;
	}

	@Override
	protected int maxProgress() {
		return UNPOWERED_INTERVAL;
	}

	public boolean toggleSort(Item item) {
		boolean added;
		if (sortList.contains(item)) {
			sortList.remove(item);
			added = false;
		} else {
			sortList.add(item);
			added = true;
		}
		setChanged();
		return added;
	}

	public void clearSort() {
		sortList.clear();
		setChanged();
	}

	public int sortCount() {
		return sortList.size();
	}

	// ---- Three-faced sided access ----

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.UP) {
			return INPUT;
		}
		if (side == Direction.DOWN) {
			return SORTED;
		}
		return REJECT;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == 0 && dir == Direction.UP;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		if (dir == Direction.DOWN) {
			return slot >= 1 && slot <= 4;
		}
		if (dir == Direction.UP) {
			return false;
		}
		return slot >= 5 && slot <= 8;
	}

	// ---- Sorting ----

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		ItemStack input = inventory.get(0);
		if (input.isEmpty()) {
			progress = 0;
			return;
		}

		progress++;
		boolean powered = (progress % POWERED_INTERVAL == 0) && MachinePower.draw(world, pos, ENERGY_PER_OP);
		if (powered) {
			route(input, BATCH_POWERED);
			progress = 0;
		} else if (progress >= UNPOWERED_INTERVAL) {
			route(input, BATCH_UNPOWERED);
			progress = 0;
		}
	}

	private void route(ItemStack input, int batch) {
		ItemStack moving = input.copyWithCount(Math.min(batch, input.getCount()));
		int before = moving.getCount();
		int[] target = sortList.contains(input.getItem()) ? SORTED : REJECT;
		moving = addToRange(moving, target);
		int moved = before - moving.getCount();
		if (moved > 0) {
			input.shrink(moved);
			setChanged();
		}
	}

	private ItemStack addToRange(ItemStack stack, int[] slots) {
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int slot : slots) {
				if (stack.isEmpty()) {
					break;
				}
				ItemStack cur = inventory.get(slot);
				if (pass == 0) {
					if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, stack)) {
						int space = cur.getMaxStackSize() - cur.getCount();
						int move = Math.min(space, stack.getCount());
						cur.grow(move);
						stack.shrink(move);
					}
				} else if (cur.isEmpty()) {
					inventory.set(slot, stack.copyWithCount(stack.getCount()));
					stack.setCount(0);
				}
			}
		}
		return stack;
	}

	// ---- Persistence ----

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		ListTag list = new ListTag();
		for (Item item : sortList) {
			list.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
		}
		nbt.put("SortList", list);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		sortList.clear();
		ListTag list = nbt.getList("SortList", Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				sortList.add(BuiltInRegistries.ITEM.get(id));
			}
		}
	}
}
