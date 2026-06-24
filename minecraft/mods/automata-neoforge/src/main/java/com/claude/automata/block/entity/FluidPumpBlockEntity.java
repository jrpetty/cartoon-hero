package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The Fluid Pump — fills empty buckets from a water or lava source directly
 * below it, bridging fluids into the item network. Water sources are renewable
 * and left in place; lava sources are consumed. Slow by hand, fast with power.
 *
 * <p>Slot 0 is the empty-bucket intake (top / sides); slot 1 is the filled-
 * bucket outtake (bottom).
 */
public class FluidPumpBlockEntity extends MachineBlockEntity {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int[] INPUTS = {INPUT_SLOT};
	private static final int PUMP_TICKS = 100;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;

	public FluidPumpBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FLUID_PUMP.get(), pos, state, 2);
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
		return stack.is(Items.BUCKET);
	}

	@Override
	protected int maxProgress() {
		return PUMP_TICKS;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (!inventory.get(INPUT_SLOT).is(Items.BUCKET)) {
			progress = 0;
			return;
		}

		BlockPos below = pos.below();
		FluidState fluid = world.getFluidState(below);
		if (!fluid.isSource()) {
			progress = 0;
			return;
		}

		Item result;
		boolean consumeSource;
		if (fluid.is(FluidTags.WATER)) {
			result = Items.WATER_BUCKET;
			consumeSource = false;
		} else if (fluid.is(FluidTags.LAVA)) {
			result = Items.LAVA_BUCKET;
			consumeSource = true;
		} else {
			progress = 0;
			return;
		}

		if (!canAcceptOutput(result, 1)) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress >= maxProgress()) {
			progress = 0;
			inventory.get(INPUT_SLOT).shrink(1);
			pushOutput(result, 1);
			if (consumeSource) {
				world.removeBlock(below, false);
			}
			setChanged();
		}
	}
}
