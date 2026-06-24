package com.claude.automata.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base class for simple single-input / single-output processing machines that
 * follow Automata's signature rhythm: they work unpowered (slowly) and run
 * several times faster when an adjacent Combustion Dynamo can supply energy.
 *
 * <p>Slot 0 is the input (top / sides for hoppers); slot 1 is the output
 * (bottom for hoppers). Subclasses only describe their recipe via
 * {@link #process(Item)} and their base (unpowered) speed via {@link #baseTicks()}.
 */
public abstract class GrindingMachineBlockEntity extends MachineBlockEntity {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int[] INPUTS = {INPUT_SLOT};

	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;

	/** The result of processing one input item: which item to make and how many. */
	public record ProcessResult(Item item, int count) {
	}

	protected GrindingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, 2);
	}

	/** Unpowered ticks per operation; a powered machine advances {@value #POWERED_STEP}x faster. */
	protected abstract int baseTicks();

	/** The result for {@code input}, or null if this machine can't process it. */
	protected abstract ProcessResult process(Item input);

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
		return process(stack.getItem()) != null;
	}

	@Override
	protected int maxProgress() {
		return baseTicks();
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		ItemStack input = inventory.get(INPUT_SLOT);
		if (input.isEmpty()) {
			progress = 0;
			return;
		}

		ProcessResult result = process(input.getItem());
		if (result == null || !canAcceptOutput(result.item(), result.count())) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress >= maxProgress()) {
			progress = 0;
			input.decrement(1);
			pushOutput(result.item(), result.count());
			markDirty();
		}
	}
}
