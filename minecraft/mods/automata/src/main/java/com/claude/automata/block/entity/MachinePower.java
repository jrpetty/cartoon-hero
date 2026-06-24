package com.claude.automata.block.entity;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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
	public static boolean draw(World world, BlockPos pos, int amount) {
		for (Direction dir : Direction.values()) {
			BlockEntity be = world.getBlockEntity(pos.offset(dir));
			if (be instanceof PowerSource source && source.extractEnergy(amount)) {
				return true;
			}
		}
		return false;
	}
}
