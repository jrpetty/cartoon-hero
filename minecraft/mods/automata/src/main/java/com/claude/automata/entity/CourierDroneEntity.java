package com.claude.automata.entity;

import com.claude.automata.logistics.InventoryTransfer;
import com.claude.automata.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A Courier Drone — a flying cargo entity dispatched by a Drone Bay. It climbs
 * to a cruise altitude (set at the bay), flies over the terrain to the
 * destination bay, then descends, deposits its payload, and despawns.
 */
public class CourierDroneEntity extends Entity {
	private static final double SPEED = 0.6;
	private static final int PHASE_ASCEND = 0;
	private static final int PHASE_CRUISE = 1;
	private static final int PHASE_DESCEND = 2;

	private BlockPos targetPos;
	private int cruiseY = 220;
	private int phase = PHASE_ASCEND;
	private final List<ItemStack> payload = new ArrayList<>();

	// Keep the drone's own chunk loaded as it flies so it never stalls over
	// unloaded terrain; the loaded "bubble" follows it and is released as it moves.
	private boolean hasForcedChunk = false;
	private int forcedChunkX;
	private int forcedChunkZ;

	public CourierDroneEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		setNoGravity(true);
	}

	public void setMission(BlockPos target, int cruiseAltitude, List<ItemStack> items) {
		this.targetPos = target.toImmutable();
		this.cruiseY = Math.min(cruiseAltitude, getWorld().getTopY() - 2);
		this.phase = PHASE_ASCEND;
		payload.clear();
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				payload.add(stack.copy());
			}
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			return;
		}
		if (targetPos == null) {
			discard();
			return;
		}

		Vec3d here = getPos();
		double destX = targetPos.getX() + 0.5;
		double destZ = targetPos.getZ() + 0.5;
		Vec3d goal;

		switch (phase) {
			case PHASE_ASCEND -> {
				goal = new Vec3d(here.x, cruiseY, here.z);
				if (here.y >= cruiseY - 0.5) {
					phase = PHASE_CRUISE;
				}
			}
			case PHASE_CRUISE -> {
				goal = new Vec3d(destX, cruiseY, destZ);
				double horiz = Math.hypot(here.x - destX, here.z - destZ);
				if (horiz < 0.8) {
					phase = PHASE_DESCEND;
				}
			}
			default -> {
				goal = new Vec3d(destX, targetPos.getY() + 0.6, destZ);
				if (here.distanceTo(goal) < 0.8) {
					deliver();
					discard();
					return;
				}
			}
		}

		Vec3d diff = goal.subtract(here);
		double dist = diff.length();
		if (dist > 1.0e-4) {
			Vec3d step = diff.normalize().multiply(Math.min(SPEED, dist));
			setPosition(here.x + step.x, here.y + step.y, here.z + step.z);
		}
		updateForcedChunk();
	}

	/** Force-load the chunk the drone currently occupies, releasing the previous one. */
	private void updateForcedChunk() {
		if (!(getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		int cx = getBlockX() >> 4;
		int cz = getBlockZ() >> 4;
		if (hasForcedChunk && cx == forcedChunkX && cz == forcedChunkZ) {
			return;
		}
		if (hasForcedChunk) {
			serverWorld.setChunkForced(forcedChunkX, forcedChunkZ, false);
		}
		serverWorld.setChunkForced(cx, cz, true);
		forcedChunkX = cx;
		forcedChunkZ = cz;
		hasForcedChunk = true;
	}

	private void releaseForcedChunk() {
		if (hasForcedChunk && getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.setChunkForced(forcedChunkX, forcedChunkZ, false);
		}
		hasForcedChunk = false;
	}

	@Override
	public void remove(RemovalReason reason) {
		releaseForcedChunk();
		super.remove(reason);
	}

	private void deliver() {
		BlockEntity be = getWorld().getBlockEntity(targetPos);
		for (ItemStack stack : payload) {
			ItemStack leftover = stack;
			if (be instanceof Inventory inv) {
				leftover = InventoryTransfer.insert(inv, stack, Direction.UP);
			}
			if (!leftover.isEmpty()) {
				dropStack(leftover);
			}
		}
		payload.clear();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		targetPos = nbt.contains("Target") ? BlockPos.fromLong(nbt.getLong("Target")) : null;
		cruiseY = nbt.getInt("CruiseY");
		phase = nbt.getInt("Phase");
		payload.clear();
		NbtList list = nbt.getList("Payload", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			ItemStack.fromNbt(getWorld().getRegistryManager(), list.getCompound(i)).ifPresent(payload::add);
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (targetPos != null) {
			nbt.putLong("Target", targetPos.asLong());
		}
		nbt.putInt("CruiseY", cruiseY);
		nbt.putInt("Phase", phase);
		NbtList list = new NbtList();
		for (ItemStack stack : payload) {
			if (!stack.isEmpty()) {
				list.add(stack.encode(getWorld().getRegistryManager()));
			}
		}
		nbt.put("Payload", list);
	}

	public static CourierDroneEntity create(World world, Vec3d pos, BlockPos target, int cruiseY, List<ItemStack> items) {
		CourierDroneEntity drone = new CourierDroneEntity(ModEntities.COURIER_DRONE, world);
		drone.setPosition(pos.x, pos.y, pos.z);
		drone.setMission(target, cruiseY, items);
		return drone;
	}
}
