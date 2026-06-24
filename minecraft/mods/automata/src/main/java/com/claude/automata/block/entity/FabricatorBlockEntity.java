package com.claude.automata.block.entity;

import com.claude.automata.recipe.FabricatorRecipes;
import com.claude.automata.registry.ModBlockEntities;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Fabricator — an auto-assembler that replaces the crafting table.
 *
 * <p>Slots 0-8 are inputs (top / sides for hoppers); slot 9 is the output
 * (bottom for hoppers). When the loaded inputs exactly match a
 * {@link FabricatorRecipes} entry, it assembles the result and consumes the
 * ingredients.
 *
 * <p>Unlike the Forge Core, the Fabricator runs without power (so it can build
 * the first Dynamo) — but it assembles {@value #POWERED_STEP}x faster when an
 * adjacent Combustion Dynamo can supply energy.
 */
public class FabricatorBlockEntity extends MachineBlockEntity {
	private static final int OUTPUT_SLOT = 9;
	private static final int ASSEMBLE_TICKS = 200;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;
	private static final int[] INPUTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

	public FabricatorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FABRICATOR, pos, state, 10);
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
		// Accept anything into the input slots; the recipe matcher decides what
		// actually assembles. This keeps loading (by hand or hopper) simple.
		return true;
	}

	@Override
	protected int maxProgress() {
		return ASSEMBLE_TICKS;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		Map<Item, Integer> inputs = collectInputs();
		FabricatorRecipes.Recipe recipe = FabricatorRecipes.match(inputs);
		if (recipe == null || !canAcceptOutput(recipe.resultItem(), recipe.resultCount())) {
			progress = 0;
			return;
		}

		// Runs unpowered at 1x; an adjacent Dynamo accelerates it.
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
