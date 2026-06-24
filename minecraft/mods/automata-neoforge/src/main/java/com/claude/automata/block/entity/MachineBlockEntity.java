package com.claude.automata.block.entity;

import com.claude.automata.screen.MachineScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Shared behaviour for Automata's machines.
 *
 * <p>Implements {@link WorldlyContainer} so hoppers can push items into the input
 * slots (from the top or sides) and pull finished goods out of the output slot
 * (from the bottom). It also exposes hand-feed / hand-extract helpers used by
 * {@code MachineBlock} so the machines are usable before you have hoppers — the
 * manual twist at the start.
 *
 * <p>Subclasses define the slot layout, what counts as valid input, and the
 * per-tick processing in {@link #tick(Level, BlockPos, BlockState)}.
 */
public abstract class MachineBlockEntity extends BlockEntity
		implements WorldlyContainer, MenuProvider {
	public static final int MAX_UPGRADES = 3;
	protected final NonNullList<ItemStack> inventory;
	protected int progress = 0;
	protected int speedUpgrades = 0;
	protected int efficiencyUpgrades = 0;

	/** Syncs progress (0) and the per-operation max (1) to an open screen. */
	private final ContainerData propertyDelegate = new ContainerData() {
		@Override
		public int get(int index) {
			return index == 0 ? progress : maxProgress();
		}

		@Override
		public void set(int index, int value) {
			if (index == 0) {
				progress = value;
			}
		}

		@Override
		public int getCount() {
			return 2;
		}
	};

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
		super(type, pos, state);
		this.inventory = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	// ---- Subclass contract ----

	/** Slots that accept input (top/side facing for hoppers). */
	protected abstract int[] inputSlots();

	/** The single output slot (bottom facing for hoppers). */
	protected abstract int outputSlot();

	/** Whether this machine will accept the given stack as input at all. */
	protected abstract boolean isValidInput(ItemStack stack);

	/** Number of ticks a single operation takes. */
	protected abstract int maxProgress();

	/** Called every server tick for a placed machine. */
	public abstract void tick(Level world, BlockPos pos, BlockState state);

	// ---- Upgrade modules ----

	/** Whether this machine benefits from upgrade modules (processing machines do). */
	public boolean usesUpgrades() {
		return false;
	}

	/** Whether right-clicking opens a screen (the single-IO processing machines). */
	public boolean hasScreen() {
		return false;
	}

	public ContainerData getPropertyDelegate() {
		return propertyDelegate;
	}

	// ---- MenuProvider ----

	@Override
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new MachineScreenHandler(syncId, playerInventory, this);
	}

	/** Install a Speed/Efficiency module. Returns true if it was accepted. */
	public boolean installUpgrade(Item item) {
		if (!usesUpgrades()) {
			return false;
		}
		if (item == com.claude.automata.registry.ModItems.SPEED_UPGRADE.get() && speedUpgrades < MAX_UPGRADES) {
			speedUpgrades++;
			setChanged();
			return true;
		}
		if (item == com.claude.automata.registry.ModItems.EFFICIENCY_UPGRADE.get() && efficiencyUpgrades < MAX_UPGRADES) {
			efficiencyUpgrades++;
			setChanged();
			return true;
		}
		return false;
	}

	/** Pop all installed modules back out as items, resetting the machine to base. */
	public java.util.List<ItemStack> removeUpgrades() {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		if (speedUpgrades > 0) {
			out.add(new ItemStack(com.claude.automata.registry.ModItems.SPEED_UPGRADE.get(), speedUpgrades));
		}
		if (efficiencyUpgrades > 0) {
			out.add(new ItemStack(com.claude.automata.registry.ModItems.EFFICIENCY_UPGRADE.get(), efficiencyUpgrades));
		}
		speedUpgrades = 0;
		efficiencyUpgrades = 0;
		setChanged();
		return out;
	}

	public int getSpeedUpgrades() {
		return speedUpgrades;
	}

	public int getEfficiencyUpgrades() {
		return efficiencyUpgrades;
	}

	/** Progress added per active tick: +1 per speed module. */
	protected int speedStep(int base) {
		return base * (1 + speedUpgrades);
	}

	/** Energy cost reduced by 25% per efficiency module (never below 1). */
	protected int reduceEnergy(int base) {
		int reduced = base - (base * efficiencyUpgrades) / 4;
		return Math.max(1, reduced);
	}

	// ---- Helpers shared by subclasses ----

	protected boolean isInputSlot(int slot) {
		for (int s : inputSlots()) {
			if (s == slot) {
				return true;
			}
		}
		return false;
	}

	/** Can {@code count} more of {@code result} be placed in the output slot? */
	protected boolean canAcceptOutput(Item result, int count) {
		ItemStack out = inventory.get(outputSlot());
		if (out.isEmpty()) {
			return true;
		}
		return out.is(result) && out.getCount() + count <= out.getMaxStackSize();
	}

	protected void pushOutput(Item result, int count) {
		ItemStack out = inventory.get(outputSlot());
		if (out.isEmpty()) {
			inventory.set(outputSlot(), new ItemStack(result, count));
		} else {
			out.grow(count);
		}
	}

	// ---- Manual interaction (used by MachineBlock) ----

	/** Insert as much of the player's held stack as will fit. Returns true if anything went in. */
	public boolean insertFromPlayer(Player player, InteractionHand hand, ItemStack held) {
		if (held.isEmpty() || !isValidInput(held)) {
			return false;
		}
		int before = held.getCount();
		for (int slot : inputSlots()) {
			if (held.isEmpty()) {
				break;
			}
			ItemStack cur = inventory.get(slot);
			if (cur.isEmpty()) {
				inventory.set(slot, held.copyWithCount(held.getCount()));
				held.setCount(0);
			} else if (ItemStack.isSameItemSameComponents(cur, held)) {
				int space = cur.getMaxStackSize() - cur.getCount();
				int move = Math.min(space, held.getCount());
				cur.grow(move);
				held.shrink(move);
			}
		}
		if (held.getCount() == before) {
			return false;
		}
		setChanged();
		return true;
	}

	/** Hand the contents of the output slot to the player. */
	public void extractToPlayer(Player player) {
		ItemStack out = inventory.get(outputSlot());
		if (out.isEmpty()) {
			return;
		}
		player.getInventory().placeItemBackInInventory(out);
		inventory.set(outputSlot(), ItemStack.EMPTY);
		setChanged();
	}

	// ---- WorldlyContainer ----

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.DOWN) {
			return new int[]{outputSlot()};
		}
		return inputSlots();
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
		return isInputSlot(slot) && isValidInput(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == outputSlot();
	}

	// ---- Container ----

	@Override
	public int getContainerSize() {
		return inventory.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : inventory) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return inventory.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(inventory, slot, amount);
		if (!result.isEmpty()) {
			setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(inventory, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		inventory.set(slot, stack);
		if (stack.getCount() > getMaxStackSize()) {
			stack.setCount(getMaxStackSize());
		}
		setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		inventory.clear();
	}

	// ---- Persistence ----

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		ContainerHelper.saveAllItems(nbt, inventory, registries);
		nbt.putInt("Progress", progress);
		nbt.putInt("SpeedUpgrades", speedUpgrades);
		nbt.putInt("EfficiencyUpgrades", efficiencyUpgrades);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		ContainerHelper.loadAllItems(nbt, inventory, registries);
		progress = nbt.getInt("Progress");
		speedUpgrades = nbt.getInt("SpeedUpgrades");
		efficiencyUpgrades = nbt.getInt("EfficiencyUpgrades");
	}
}
