package com.claude.automata.block.entity;

/**
 * Anything a machine can draw energy from: the Combustion Dynamo, Solar Array,
 * Thermal Generator and Capacitor Bank all implement this. {@link MachinePower}
 * scans a machine's neighbours for these.
 */
public interface PowerSource {
	/**
	 * Remove {@code amount} energy if it is available.
	 *
	 * @return true if the energy was supplied.
	 */
	boolean extractEnergy(int amount);
}
