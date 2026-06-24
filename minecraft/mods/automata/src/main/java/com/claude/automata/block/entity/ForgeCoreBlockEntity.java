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
 * (bottom for hoppers). It needs no fuel: it builds heat passively and smelts
 * one item every {@value #SMELT_TICKS} ticks.
 */
public class ForgeCoreBlockEntity extends MachineBlockEntity {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 1;
	private static final int SMELT_TICKS = 200;
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

		progress++;
		if (progress >= maxProgress()) {
			progress = 0;
			input.decrement(1);
			pushOutput(result, 1);
			markDirty();
		}
	}
}
