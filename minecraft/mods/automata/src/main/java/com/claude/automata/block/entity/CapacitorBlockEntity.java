package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Capacitor Bank — a large energy store. It pulls from adjacent power
 * sources to fill itself and is itself a power source for adjacent machines, so
 * it both smooths supply through fuel gaps and relays power one block further
 * (chain them to extend a grid before the dedicated cables exist).
 */
public class CapacitorBlockEntity extends EnergyBlockEntity {
	private static final int MAX_ENERGY = 100000;
	private static final int CHARGE_RATE = 80;

	public CapacitorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CAPACITOR, pos, state);
	}

	@Override
	protected int maxEnergy() {
		return MAX_ENERGY;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (energy <= MAX_ENERGY - CHARGE_RATE && MachinePower.draw(world, pos, CHARGE_RATE)) {
			addEnergy(CHARGE_RATE);
		}
	}
}
