package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Solar Array — passive, fuel-free power, but only in daylight with a clear
 * view of the sky. Output drops in rain and stops at night, so it pairs well
 * with a Capacitor Bank to carry a base through the dark.
 */
public class SolarArrayBlockEntity extends EnergyBlockEntity {
	private static final int MAX_ENERGY = 10000;
	private static final int CLEAR_RATE = 16;
	private static final int RAIN_RATE = 5;

	public SolarArrayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOLAR_ARRAY, pos, state);
	}

	@Override
	protected int maxEnergy() {
		return MAX_ENERGY;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (!world.isDay() || !world.isSkyVisible(pos.up())) {
			return;
		}
		int rate = (world.isRaining() || world.isThundering()) ? RAIN_RATE : CLEAR_RATE;
		addEnergy(rate);
	}
}
