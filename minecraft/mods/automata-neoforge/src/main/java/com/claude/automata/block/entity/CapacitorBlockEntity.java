package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
		super(ModBlockEntities.CAPACITOR.get(), pos, state);
	}

	@Override
	protected int maxEnergy() {
		return MAX_ENERGY;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (energy <= MAX_ENERGY - CHARGE_RATE && MachinePower.draw(world, pos, CHARGE_RATE)) {
			addEnergy(CHARGE_RATE);
		}
	}
}
