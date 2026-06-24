package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Thermal Generator — burns lava buckets for a large, long-lasting supply
 * of power, returning the empty bucket. A late-game alternative to the
 * Combustion Dynamo.
 *
 * <p>Slot 0 is the lava-bucket intake (top / sides); slot 1 is the empty-bucket
 * outtake (bottom). One lava bucket runs it for a long time.
 */
public class ThermalGeneratorBlockEntity extends MachineBlockEntity implements PowerSource {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int[] INPUTS = {INPUT_SLOT};

	private static final int MAX_ENERGY = 60000;
	private static final int ENERGY_PER_TICK = 60;
	private static final int LAVA_BURN_TICKS = 24000;

	private int energy = 0;
	private int burnTime = 0;

	public ThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.THERMAL_GENERATOR.get(), pos, state, 2);
	}

	@Override
	protected int[] inputSlots() {
		return INPUTS;
	}

	@Override
	protected int outputSlot() {
		return OUTPUT_SLOT;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return stack.is(Items.LAVA_BUCKET);
	}

	@Override
	protected int maxProgress() {
		return LAVA_BURN_TICKS;
	}

	@Override
	public boolean extractEnergy(int amount) {
		if (energy >= amount) {
			energy -= amount;
			setChanged();
			return true;
		}
		return false;
	}

	public boolean isBurning() {
		return burnTime > 0;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		boolean dirty = false;

		if (burnTime <= 0 && energy < MAX_ENERGY) {
			ItemStack input = inventory.get(INPUT_SLOT);
			if (input.is(Items.LAVA_BUCKET) && canAcceptOutput(Items.BUCKET, 1)) {
				input.shrink(1);
				pushOutput(Items.BUCKET, 1);
				burnTime = LAVA_BURN_TICKS;
				dirty = true;
			}
		}

		if (burnTime > 0) {
			burnTime--;
			energy = Math.min(MAX_ENERGY, energy + ENERGY_PER_TICK);
			dirty = true;
		}

		if (dirty) {
			setChanged();
		}
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("Energy", energy);
		nbt.putInt("BurnTime", burnTime);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		energy = nbt.getInt("Energy");
		burnTime = nbt.getInt("BurnTime");
	}
}
