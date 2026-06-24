package com.claude.automata.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

	public abstract void tick(World world, BlockPos pos, BlockState state);

	@Override
	public boolean extractEnergy(int amount) {
		if (energy >= amount) {
			energy -= amount;
			markDirty();
			return true;
		}
		return false;
	}

	protected void addEnergy(int amount) {
		int next = Math.min(maxEnergy(), energy + amount);
		if (next != energy) {
			energy = next;
			markDirty();
		}
	}

	public int getEnergy() {
		return energy;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Energy", energy);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		energy = nbt.getInt("Energy");
	}
}
