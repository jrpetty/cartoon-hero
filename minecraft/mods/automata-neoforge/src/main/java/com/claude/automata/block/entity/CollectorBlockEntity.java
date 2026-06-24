package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Item Collector — vacuums up dropped items in a radius into its output
 * slots, feeding them into the hopper network. The glue for tree farms, mob
 * grinders and Auto-Miner overflow. A small radius by hand; a larger radius
 * when powered.
 */
public class CollectorBlockEntity extends OutputMachineBlockEntity {
	private static final int SIZE = 9;
	private static final int SCAN_INTERVAL = 8;
	private static final int RADIUS_UNPOWERED = 2;
	private static final int RADIUS_POWERED = 5;
	private static final int ENERGY_PER_SCAN = 10;

	public CollectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COLLECTOR.get(), pos, state, SIZE);
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		progress++;
		if (progress < SCAN_INTERVAL) {
			return;
		}
		progress = 0;

		if (!hasEmptySlot()) {
			return;
		}

		int radius = MachinePower.draw(world, pos, ENERGY_PER_SCAN) ? RADIUS_POWERED : RADIUS_UNPOWERED;
		AABB area = new AABB(
				pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
				pos.getX() + radius + 1, pos.getY() + radius + 1, pos.getZ() + radius + 1);

		List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive);
		boolean changed = false;
		for (ItemEntity item : items) {
			ItemStack leftover = addOutput(item.getItem());
			if (leftover.isEmpty()) {
				item.discard();
			} else {
				item.setItem(leftover);
			}
			changed = true;
			if (!hasEmptySlot()) {
				break;
			}
		}
		if (changed) {
			setChanged();
		}
	}
}
