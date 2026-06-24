package com.claude.automata.block.entity;

import com.claude.automata.screen.MachineScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Shared behaviour for Automata's machines.
 *
 * <p>Implements {@link SidedInventory} so hoppers can push items into the input
 * slots (from the top or sides) and pull finished goods out of the output slot
 * (from the bottom). It also exposes hand-feed / hand-extract helpers used by
 * {@code MachineBlock} so the machines are usable before you have hoppers — the
 * manual twist at the start.
 *
 * <p>Subclasses define the slot layout, what counts as valid input, and the
 * per-tick processing in {@link #tick(World, BlockPos, BlockState)}.
 */
public abstract class MachineBlockEntity extends BlockEntity
		implements SidedInventory, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int MAX_UPGRADES = 3;
	protected final DefaultedList<ItemStack> inventory;
	protected int progress = 0;
	protected int speedUpgrades = 0;
	protected int efficiencyUpgrades = 0;

	/** Syncs progress (0) and the per-operation max (1) to an open screen. */
	private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
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
		public int size() {
			return 2;
		}
	};

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
		super(type, pos, state);
		this.inventory = DefaultedList.ofSize(size, ItemStack.EMPTY);
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
	public abstract void tick(World world, BlockPos pos, BlockState state);

	// ---- Upgrade modules ----

	/** Whether this machine benefits from upgrade modules (processing machines do). */
	public boolean usesUpgrades() {
		return false;
	}

	/** Whether right-clicking opens a screen (the single-IO processing machines). */
	public boolean hasScreen() {
		return false;
	}

	public PropertyDelegate getPropertyDelegate() {
		return propertyDelegate;
	}

	// ---- ExtendedScreenHandlerFactory ----

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable(getCachedState().getBlock().getTranslationKey());
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new MachineScreenHandler(syncId, playerInventory, pos);
	}

	/** Install a Speed/Efficiency module. Returns true if it was accepted. */
	public boolean installUpgrade(Item item) {
		if (!usesUpgrades()) {
			return false;
		}
		if (item == com.claude.automata.registry.ModItems.SPEED_UPGRADE && speedUpgrades < MAX_UPGRADES) {
			speedUpgrades++;
			markDirty();
			return true;
		}
		if (item == com.claude.automata.registry.ModItems.EFFICIENCY_UPGRADE && efficiencyUpgrades < MAX_UPGRADES) {
			efficiencyUpgrades++;
			markDirty();
			return true;
		}
		return false;
	}

	/** Pop all installed modules back out as items, resetting the machine to base. */
	public java.util.List<ItemStack> removeUpgrades() {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		if (speedUpgrades > 0) {
			out.add(new ItemStack(com.claude.automata.registry.ModItems.SPEED_UPGRADE, speedUpgrades));
		}
		if (efficiencyUpgrades > 0) {
			out.add(new ItemStack(com.claude.automata.registry.ModItems.EFFICIENCY_UPGRADE, efficiencyUpgrades));
		}
		speedUpgrades = 0;
		efficiencyUpgrades = 0;
		markDirty();
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
		return out.isOf(result) && out.getCount() + count <= out.getMaxCount();
	}

	protected void pushOutput(Item result, int count) {
		ItemStack out = inventory.get(outputSlot());
		if (out.isEmpty()) {
			inventory.set(outputSlot(), new ItemStack(result, count));
		} else {
			out.increment(count);
		}
	}

	// ---- Manual interaction (used by MachineBlock) ----

	/** Insert as much of the player's held stack as will fit. Returns true if anything went in. */
	public boolean insertFromPlayer(PlayerEntity player, Hand hand, ItemStack held) {
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
			} else if (ItemStack.areItemsAndComponentsEqual(cur, held)) {
				int space = cur.getMaxCount() - cur.getCount();
				int move = Math.min(space, held.getCount());
				cur.increment(move);
				held.decrement(move);
			}
		}
		if (held.getCount() == before) {
			return false;
		}
		markDirty();
		return true;
	}

	/** Hand the contents of the output slot to the player. */
	public void extractToPlayer(PlayerEntity player) {
		ItemStack out = inventory.get(outputSlot());
		if (out.isEmpty()) {
			return;
		}
		player.getInventory().offerOrDrop(out);
		inventory.set(outputSlot(), ItemStack.EMPTY);
		markDirty();
	}

	// ---- SidedInventory ----

	@Override
	public int[] getAvailableSlots(Direction side) {
		if (side == Direction.DOWN) {
			return new int[]{outputSlot()};
		}
		return inputSlots();
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return isInputSlot(slot) && isValidInput(stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == outputSlot();
	}

	// ---- Inventory ----

	@Override
	public int size() {
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
	public ItemStack getStack(int slot) {
		return inventory.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(inventory, slot, amount);
		if (!result.isEmpty()) {
			markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(inventory, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		inventory.set(slot, stack);
		if (stack.getCount() > getMaxCountPerStack()) {
			stack.setCount(getMaxCountPerStack());
		}
		markDirty();
	}

	@Override
	public int getMaxCountPerStack() {
		return 64;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return true;
	}

	@Override
	public void clear() {
		inventory.clear();
	}

	// ---- Persistence ----

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, inventory, registries);
		nbt.putInt("Progress", progress);
		nbt.putInt("SpeedUpgrades", speedUpgrades);
		nbt.putInt("EfficiencyUpgrades", efficiencyUpgrades);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		Inventories.readNbt(nbt, inventory, registries);
		progress = nbt.getInt("Progress");
		speedUpgrades = nbt.getInt("SpeedUpgrades");
		efficiencyUpgrades = nbt.getInt("EfficiencyUpgrades");
	}
}
