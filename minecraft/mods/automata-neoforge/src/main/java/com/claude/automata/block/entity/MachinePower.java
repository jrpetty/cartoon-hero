package com.claude.automata.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Helper for machines that draw power from an adjacent Combustion Dynamo.
 *
 * <p>"Powers items around it" is modelled as the six directly-adjacent blocks:
 * a machine asks each neighbour for energy and the first Dynamo that can supply
 * it wins.
 */
public final class MachinePower {
	private MachinePower() {
	}

	/**
	 * Attempt to draw {@code amount} energy from an adjacent Dynamo.
	 *
	 * @return true if a neighbouring Dynamo supplied the energy.
	 */
	public static boolean draw(Level world, BlockPos pos, int amount) {
		for (Direction dir : Direction.values()) {
			BlockEntity be = world.getBlockEntity(pos.relative(dir));
			if (be instanceof PowerSource source && source.extractEnergy(amount)) {
				return true;
			}
		}
		return false;
	}
}
