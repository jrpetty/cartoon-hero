package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Power Conduit — a cheap cable that carries energy between blocks that
 * aren't touching. Each conduit is a small {@link PowerSource} that refills
 * itself from adjacent sources every tick, so a line of conduits relays power
 * one block per tick from a generator to distant machines.
 */
public class ConduitBlockEntity extends EnergyBlockEntity {
	private static final int MAX_ENERGY = 2000;
	private static final int TRANSFER_RATE = 100;

	public ConduitBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CONDUIT.get(), pos, state);
	}

	@Override
	protected int maxEnergy() {
		return MAX_ENERGY;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (energy <= MAX_ENERGY - TRANSFER_RATE && MachinePower.draw(world, pos, TRANSFER_RATE)) {
			addEnergy(TRANSFER_RATE);
		}
	}
}
