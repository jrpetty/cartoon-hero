package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
		super(ModBlockEntities.THERMAL_GENERATOR, pos, state, 2);
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
		return stack.isOf(Items.LAVA_BUCKET);
	}

	@Override
	protected int maxProgress() {
		return LAVA_BURN_TICKS;
	}

	@Override
	public boolean extractEnergy(int amount) {
		if (energy >= amount) {
			energy -= amount;
			markDirty();
			return true;
		}
		return false;
	}

	public boolean isBurning() {
		return burnTime > 0;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		boolean dirty = false;

		if (burnTime <= 0 && energy < MAX_ENERGY) {
			ItemStack input = inventory.get(INPUT_SLOT);
			if (input.isOf(Items.LAVA_BUCKET) && canAcceptOutput(Items.BUCKET, 1)) {
				input.decrement(1);
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
			markDirty();
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Energy", energy);
		nbt.putInt("BurnTime", burnTime);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		energy = nbt.getInt("Energy");
		burnTime = nbt.getInt("BurnTime");
	}
}
