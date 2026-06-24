package com.claude.automata.block.entity;

import com.claude.automata.recipe.SmeltingRecipes;
import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Forge Core — an auto-smelter that replaces the furnace.
 *
 * <p>Slot 0 is the input (top / sides for hoppers); slot 1 is the output
 * (bottom for hoppers). It does not burn fuel directly: it requires
 * <em>power</em> drawn from an adjacent Combustion Dynamo. With no powered
 * Dynamo next to it, smelting stalls (and resumes where it left off once power
 * returns). A fully powered Forge Core smelts one item every
 * {@value #SMELT_TICKS} ticks.
 */
public class ForgeCoreBlockEntity extends MachineBlockEntity {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int SMELT_TICKS = 100;
	private static final int ENERGY_PER_TICK = 10;
	private static final int[] INPUTS = {INPUT_SLOT};

	public ForgeCoreBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FORGE_CORE, pos, state, 2);
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
		return SmeltingRecipes.canSmelt(stack.getItem());
	}

	@Override
	protected int maxProgress() {
		return SMELT_TICKS;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		ItemStack input = inventory.get(INPUT_SLOT);
		if (input.isEmpty()) {
			progress = 0;
			return;
		}

		Item result = SmeltingRecipes.result(input.getItem());
		if (result == null || !canAcceptOutput(result, 1)) {
			progress = 0;
			return;
		}

		// Requires power: stall (keeping progress) until an adjacent Dynamo can supply it.
		if (!MachinePower.draw(world, pos, ENERGY_PER_TICK)) {
			return;
		}

		progress++;
		if (progress >= maxProgress()) {
			progress = 0;
			input.decrement(1);
			pushOutput(result, 1);
			markDirty();
		}
	}
}
