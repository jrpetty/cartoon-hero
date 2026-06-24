package com.claude.automata.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for blocks that are purely an energy buffer and a {@link PowerSource}
 * (no item inventory) — the Solar Array and Capacitor Bank.
 */
public abstract class EnergyBlockEntity extends BlockEntity implements PowerSource {
	protected int energy = 0;

	protected EnergyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	protected abstract int maxEnergy();

	public abstract void tick(Level world, BlockPos pos, BlockState state);

	@Override
	public boolean extractEnergy(int amount) {
		if (energy >= amount) {
			energy -= amount;
			setChanged();
			return true;
		}
		return false;
	}

	protected void addEnergy(int amount) {
		int next = Math.min(maxEnergy(), energy + amount);
		if (next != energy) {
			energy = next;
			setChanged();
		}
	}

	public int getEnergy() {
		return energy;
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("Energy", energy);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		energy = nbt.getInt("Energy");
	}
}
