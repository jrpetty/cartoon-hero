package com.claude.automata.block.entity;

import com.claude.automata.recipe.Fuels;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Combustion Dynamo — burns fuel to generate energy for adjacent machines,
 * and emits Ash as a byproduct.
 *
 * <p>Slot 0 is the fuel intake (top / sides for hoppers); slot 1 is the
 * byproduct outtake (bottom for hoppers). While fuel is burning the Dynamo
 * fills its energy buffer at {@value #ENERGY_PER_TICK}/tick and drops one Ash
 * into the output every {@value #ASH_INTERVAL} ticks. Adjacent Forge Cores and
 * Fabricators pull energy from the buffer via {@link MachinePower}.
 */
public class GeneratorBlockEntity extends MachineBlockEntity implements PowerSource {
	private static final int FUEL_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int[] INPUTS = {FUEL_SLOT};

	public static final int MAX_ENERGY = 30000;
	private static final int ENERGY_PER_TICK = 30;
	private static final int ASH_INTERVAL = 200;

	private int energy = 0;
	private int burnTime = 0;

	public GeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GENERATOR, pos, state, 2);
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
		return Fuels.isFuel(stack.getItem());
	}

	@Override
	protected int maxProgress() {
		return ASH_INTERVAL;
	}

	/** Pull energy from the buffer if enough is available. */
	public boolean extractEnergy(int amount) {
		if (energy >= amount) {
			energy -= amount;
			markDirty();
			return true;
		}
		return false;
	}

	public int getEnergy() {
		return energy;
	}

	public boolean isBurning() {
		return burnTime > 0;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		boolean dirty = false;

		// Light a new piece of fuel when the buffer has room and nothing is burning.
		if (burnTime <= 0 && energy < MAX_ENERGY) {
			ItemStack fuel = inventory.get(FUEL_SLOT);
			int ticks = fuel.isEmpty() ? 0 : Fuels.burnTicks(fuel.getItem());
			if (ticks > 0) {
				burnTime = ticks;
				fuel.decrement(1);
				dirty = true;
			}
		}

		// Burn: accumulate energy and periodically emit Ash.
		if (burnTime > 0) {
			burnTime--;
			energy = Math.min(MAX_ENERGY, energy + ENERGY_PER_TICK);
			progress++;
			if (progress >= ASH_INTERVAL) {
				progress = 0;
				if (canAcceptOutput(ModItems.ASH, 1)) {
					pushOutput(ModItems.ASH, 1);
				}
			}
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
