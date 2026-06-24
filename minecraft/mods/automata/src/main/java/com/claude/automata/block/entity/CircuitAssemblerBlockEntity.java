package com.claude.automata.block.entity;

import com.claude.automata.recipe.CircuitRecipes;
import com.claude.automata.registry.ModBlockEntities;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Circuit Assembler — a four-input machine that builds Logic Circuits when
 * its inputs exactly match a {@link CircuitRecipes} entry. Runs unpowered but
 * slowly, faster with power; accepts upgrade modules.
 *
 * <p>Slots 0-3 are inputs (top/sides), slot 4 is the output (bottom).
 */
public class CircuitAssemblerBlockEntity extends MachineBlockEntity {
	private static final int OUTPUT_SLOT = 4;
	private static final int ASSEMBLE_TICKS = 160;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;
	private static final int[] INPUTS = {0, 1, 2, 3};

	public CircuitAssemblerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CIRCUIT_ASSEMBLER, pos, state, 5);
	}

	@Override
	public boolean usesUpgrades() {
		return true;
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
		return true;
	}

	@Override
	protected int maxProgress() {
		return ASSEMBLE_TICKS;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		Map<Item, Integer> inputs = collectInputs();
		CircuitRecipes.Recipe recipe = CircuitRecipes.match(inputs);
		if (recipe == null || !canAcceptOutput(recipe.resultItem(), recipe.resultCount())) {
			progress = 0;
			return;
		}

		int base = MachinePower.draw(world, pos, reduceEnergy(ENERGY_PER_TICK)) ? POWERED_STEP : 1;
		progress += speedStep(base);
		if (progress >= maxProgress()) {
			progress = 0;
			consume(recipe.ingredients);
			pushOutput(recipe.resultItem(), recipe.resultCount());
			markDirty();
		}
	}

	private Map<Item, Integer> collectInputs() {
		Map<Item, Integer> map = new HashMap<>();
		for (int slot : INPUTS) {
			ItemStack stack = inventory.get(slot);
			if (!stack.isEmpty()) {
				map.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return map;
	}

	private void consume(Map<Item, Integer> ingredients) {
		for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
			int remaining = entry.getValue();
			for (int slot : INPUTS) {
				if (remaining <= 0) {
					break;
				}
				ItemStack stack = inventory.get(slot);
				if (!stack.isEmpty() && stack.getItem() == entry.getKey()) {
					int take = Math.min(remaining, stack.getCount());
					stack.decrement(take);
					remaining -= take;
				}
			}
		}
	}
}
