package com.claude.automata.block.entity;

import com.claude.automata.entity.CourierDroneEntity;
import com.claude.automata.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A Drone Bay — a station that dispatches a Courier Drone to ferry its contents
 * to a linked destination bay. Hopper-fill it to send; delivered items land in
 * the destination bay's slots for a hopper to collect.
 *
 * <p>Link with the Logistics Wrench (select one bay, click another to send to
 * it). Dispatches slowly by hand, faster with power.
 */
public class DroneBayBlockEntity extends MachineBlockEntity {
	private static final Map<UUID, BlockPos> SELECTION = new ConcurrentHashMap<>();

	private static final int SIZE = 9;
	private static final int[] SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int INTERVAL_UNPOWERED = 100;
	private static final int INTERVAL_POWERED = 30;
	private static final int ENERGY_PER_DISPATCH = 50;

	private BlockPos destination;

	public DroneBayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DRONE_BAY, pos, state, SIZE);
	}

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
		return INTERVAL_UNPOWERED;
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
		return true;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (destination == null || isEmpty()) {
			progress = 0;
			return;
		}
		// Only dispatch to a real, loaded destination bay.
		if (!(world.getBlockEntity(destination) instanceof DroneBayBlockEntity)) {
			progress = 0;
			return;
		}

		boolean powered = MachinePower.draw(world, pos, ENERGY_PER_DISPATCH);
		int interval = powered ? INTERVAL_POWERED : INTERVAL_UNPOWERED;
		if (++progress < interval) {
			return;
		}
		progress = 0;

		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		List<ItemStack> payload = new ArrayList<>();
		for (int slot : SLOTS) {
			ItemStack stack = inventory.get(slot);
			if (!stack.isEmpty()) {
				payload.add(stack.copy());
				inventory.set(slot, ItemStack.EMPTY);
			}
		}
		if (payload.isEmpty()) {
			return;
		}

		Vec3d spawn = Vec3d.ofCenter(pos).add(0, 0.8, 0);
		serverWorld.spawnEntity(CourierDroneEntity.create(serverWorld, spawn, destination, payload));
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (destination != null) {
			nbt.putLong("Destination", destination.asLong());
		}
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		destination = nbt.contains("Destination") ? BlockPos.fromLong(nbt.getLong("Destination")) : null;
	}
}
