package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Harvester — reaps mature crops in a square around it and replants them,
 * collecting the produce into its output. Slow by hand, faster with power.
 */
public class HarvesterBlockEntity extends OutputMachineBlockEntity {
	private static final int SIZE = 9;
	private static final int RADIUS = 4;
	private static final int HARVEST_TICKS = 80;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;

	public HarvesterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.HARVESTER, pos, state, SIZE);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (!hasEmptySlot()) {
			return;
		}
		BlockPos target = findMatureCrop(world, pos);
		if (target == null) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress < HARVEST_TICKS) {
			return;
		}
		progress = 0;

		BlockState cropState = world.getBlockState(target);
		if (!(cropState.getBlock() instanceof CropBlock crop) || !crop.isMature(cropState)) {
			return;
		}
		if (world instanceof ServerWorld serverWorld) {
			List<ItemStack> drops = Block.getDroppedStacks(cropState, serverWorld, target, null, null, ItemStack.EMPTY);
			world.setBlockState(target, crop.withAge(0));
			for (ItemStack drop : drops) {
				ItemStack leftover = addOutput(drop);
				if (!leftover.isEmpty()) {
					ItemScatterer.spawn(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
			markDirty();
		}
	}

	private BlockPos findMatureCrop(World world, BlockPos pos) {
		int y = pos.getY();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				BlockPos p = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
				BlockState s = world.getBlockState(p);
				if (s.getBlock() instanceof CropBlock crop && crop.isMature(s)) {
					return p;
				}
			}
		}
		return null;
	}
}
