package com.claude.automata.entity;

import com.claude.automata.logistics.InventoryTransfer;
import com.claude.automata.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

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

	public CourierDroneEntity(EntityType<?> type, Level world) {
		super(type, world);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public void setMission(BlockPos target, int cruiseAltitude, List<ItemStack> items) {
		this.targetPos = target.immutable();
		this.cruiseY = Math.min(cruiseAltitude, level().getMaxBuildHeight() - 2);
		this.phase = PHASE_ASCEND;
		payload.clear();
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				payload.add(stack.copy());
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		if (targetPos == null) {
			discard();
			return;
		}

		Vec3 here = position();
		double destX = targetPos.getX() + 0.5;
		double destZ = targetPos.getZ() + 0.5;
		Vec3 goal;

		switch (phase) {
			case PHASE_ASCEND -> {
				goal = new Vec3(here.x, cruiseY, here.z);
				if (here.y >= cruiseY - 0.5) {
					phase = PHASE_CRUISE;
				}
			}
			case PHASE_CRUISE -> {
				goal = new Vec3(destX, cruiseY, destZ);
				double horiz = Math.hypot(here.x - destX, here.z - destZ);
				if (horiz < 0.8) {
					phase = PHASE_DESCEND;
				}
			}
			default -> {
				goal = new Vec3(destX, targetPos.getY() + 0.6, destZ);
				if (here.distanceTo(goal) < 0.8) {
					deliver();
					discard();
					return;
				}
			}
		}

		Vec3 diff = goal.subtract(here);
		double dist = diff.length();
		if (dist > 1.0e-4) {
			Vec3 step = diff.normalize().scale(Math.min(SPEED, dist));
			setPos(here.x + step.x, here.y + step.y, here.z + step.z);
		}
		updateForcedChunk();
	}

	/** Force-load the chunk the drone currently occupies, releasing the previous one. */
	private void updateForcedChunk() {
		if (!(level() instanceof ServerLevel serverWorld)) {
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
		if (hasForcedChunk && level() instanceof ServerLevel serverWorld) {
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
		BlockEntity be = level().getBlockEntity(targetPos);
		for (ItemStack stack : payload) {
			ItemStack leftover = stack;
			if (be instanceof Container inv) {
				leftover = InventoryTransfer.insert(inv, stack, Direction.UP);
			}
			if (!leftover.isEmpty()) {
				spawnAtLocation(leftover);
			}
		}
		payload.clear();
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		targetPos = nbt.contains("Target") ? BlockPos.of(nbt.getLong("Target")) : null;
		cruiseY = nbt.getInt("CruiseY");
		phase = nbt.getInt("Phase");
		payload.clear();
		ListTag list = nbt.getList("Payload", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			ItemStack.parse(level().registryAccess(), list.getCompound(i)).ifPresent(payload::add);
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		if (targetPos != null) {
			nbt.putLong("Target", targetPos.asLong());
		}
		nbt.putInt("CruiseY", cruiseY);
		nbt.putInt("Phase", phase);
		ListTag list = new ListTag();
		for (ItemStack stack : payload) {
			if (!stack.isEmpty()) {
				list.add(stack.save(level().registryAccess()));
			}
		}
		nbt.put("Payload", list);
	}

	public static CourierDroneEntity create(Level world, Vec3 pos, BlockPos target, int cruiseY, List<ItemStack> items) {
		CourierDroneEntity drone = new CourierDroneEntity(ModEntities.COURIER_DRONE.get(), world);
		drone.setPos(pos.x, pos.y, pos.z);
		drone.setMission(target, cruiseY, items);
		return drone;
	}
}
