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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A Courier Drone — a small flying entity dispatched by a Drone Bay to ferry a
 * payload of items to a destination bay, deposit them, and despawn. It flies in
 * a straight line (no clip, no gravity) toward its target.
 */
public class CourierDroneEntity extends Entity {
	private static final double SPEED = 0.35;

	private BlockPos targetPos;
	private final List<ItemStack> payload = new ArrayList<>();

	public CourierDroneEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		setNoGravity(true);
	}

	public void setMission(BlockPos target, List<ItemStack> items) {
		this.targetPos = target.toImmutable();
		payload.clear();
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				payload.add(stack.copy());
			}
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// No synced data — the client renders a generic drone at the tracked position.
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

		Vec3d goal = Vec3d.ofCenter(targetPos).add(0, 0.6, 0);
		Vec3d here = getPos();
		Vec3d diff = goal.subtract(here);
		double dist = diff.length();
		if (dist < 0.8) {
			deliver();
			discard();
			return;
		}
		Vec3d step = diff.normalize().multiply(Math.min(SPEED, dist));
		setPosition(here.x + step.x, here.y + step.y, here.z + step.z);
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
		NbtList list = new NbtList();
		for (ItemStack stack : payload) {
			if (!stack.isEmpty()) {
				list.add(stack.encode(getWorld().getRegistryManager()));
			}
		}
		nbt.put("Payload", list);
	}

	/** Convenience factory used by the Drone Bay. */
	public static CourierDroneEntity create(World world, Vec3d pos, BlockPos target, List<ItemStack> items) {
		CourierDroneEntity drone = new CourierDroneEntity(ModEntities.COURIER_DRONE, world);
		drone.setPosition(pos.x, pos.y, pos.z);
		drone.setMission(target, items);
		return drone;
	}
}
