package com.claude.automata.block.entity;

import com.claude.automata.entity.CourierDroneEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A Drone Bay — place it, load up to three stacks, link it to a destination bay
 * with the Logistics Wrench, set a cruise altitude, and send. It dispatches a
 * Courier Drone that flies at the chosen altitude to the destination, within the
 * range of its installed battery.
 *
 * <p>Slots 0-2 are the payload (top/sides for hoppers); slot 3 holds the battery
 * (installed by right-clicking with one; not hopper-accessible).
 */
public class DroneBayBlockEntity extends MachineBlockEntity {
	private static final Map<UUID, BlockPos> SELECTION = new ConcurrentHashMap<>();

	public static final int MIN_ALTITUDE = 140;
	public static final int MAX_ALTITUDE = 320;
	private static final int ALTITUDE_STEP = 20;
	private static final int[] PAYLOAD = {0, 1, 2};
	private static final int BATTERY_SLOT = 3;

	private BlockPos destination;
	private int cruiseAltitude = 220;

	public DroneBayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DRONE_BAY, pos, state, 4);
	}

	// ---- Battery ranges ----

	public static int rangeOf(Item battery) {
		if (battery == ModItems.DRONE_BATTERY) {
			return 300;
		}
		if (battery == ModItems.DRONE_BATTERY_ADVANCED) {
			return 800;
		}
		if (battery == ModItems.DRONE_BATTERY_ELITE) {
			return 2000;
		}
		return 0;
	}

	public static boolean isBattery(Item item) {
		return rangeOf(item) > 0;
	}

	public int range() {
		return rangeOf(inventory.get(BATTERY_SLOT).getItem());
	}

	/** Install a battery into the empty battery slot. Returns true on success. */
	public boolean installBattery(ItemStack stack) {
		if (!isBattery(stack.getItem()) || !inventory.get(BATTERY_SLOT).isEmpty()) {
			return false;
		}
		inventory.set(BATTERY_SLOT, stack.copyWithCount(1));
		markDirty();
		return true;
	}

	// ---- Linking ----

	public static void select(UUID player, BlockPos pos) {
		SELECTION.put(player, pos.toImmutable());
	}

	public static BlockPos getSelection(UUID player) {
		return SELECTION.get(player);
	}

	public static void deselect(UUID player) {
		SELECTION.remove(player);
	}

	public void setDestination(BlockPos pos) {
		this.destination = pos == null ? null : pos.toImmutable();
		markDirty();
	}

	public BlockPos getDestination() {
		return destination;
	}

	// ---- Altitude ----

	public int cycleAltitude() {
		cruiseAltitude += ALTITUDE_STEP;
		if (cruiseAltitude > MAX_ALTITUDE) {
			cruiseAltitude = MIN_ALTITUDE;
		}
		markDirty();
		return cruiseAltitude;
	}

	public int getCruiseAltitude() {
		return cruiseAltitude;
	}

	// ---- Sending ----

	/** Try to launch a drone. Returns a status message for the player. */
	public String send(World world) {
		if (destination == null) {
			return "No destination linked (use the Logistics Wrench).";
		}
		if (!(world.getBlockEntity(destination) instanceof DroneBayBlockEntity)) {
			return "Destination bay not found or not loaded.";
		}
		int range = range();
		if (range <= 0) {
			return "No battery installed.";
		}
		double dist = Math.hypot(pos.getX() - destination.getX(), pos.getZ() - destination.getZ());
		if (dist > range) {
			return "Out of range: " + (int) dist + " > " + range + " blocks.";
		}

		List<ItemStack> payload = new ArrayList<>();
		for (int slot : PAYLOAD) {
			ItemStack stack = inventory.get(slot);
			if (!stack.isEmpty()) {
				payload.add(stack.copy());
				inventory.set(slot, ItemStack.EMPTY);
			}
		}
		if (payload.isEmpty()) {
			return "Nothing loaded to send.";
		}

		if (world instanceof ServerWorld serverWorld) {
			Vec3d spawn = Vec3d.ofCenter(pos).add(0, 0.8, 0);
			serverWorld.spawnEntity(CourierDroneEntity.create(serverWorld, spawn, destination, cruiseAltitude, payload));
		}
		markDirty();
		return "Drone launched at altitude " + cruiseAltitude + "!";
	}

	// ---- Inventory (payload only; battery slot hidden from hoppers) ----

	@Override
	protected int[] inputSlots() {
		return PAYLOAD;
	}

	@Override
	protected int outputSlot() {
		return 0;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return !isBattery(stack.getItem());
	}

	@Override
	protected int maxProgress() {
		return 1;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		// No automatic ticking — drones are launched manually with the send action.
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return PAYLOAD;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return slot >= 0 && slot <= 2 && isValidInput(stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot >= 0 && slot <= 2;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (destination != null) {
			nbt.putLong("Destination", destination.asLong());
		}
		nbt.putInt("CruiseAltitude", cruiseAltitude);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		destination = nbt.contains("Destination") ? BlockPos.fromLong(nbt.getLong("Destination")) : null;
		cruiseAltitude = nbt.contains("CruiseAltitude") ? nbt.getInt("CruiseAltitude") : 220;
	}
}
