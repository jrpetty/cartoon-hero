package com.claude.automata.block.entity;

import com.claude.automata.block.FacingMachineBlock;
import com.claude.automata.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Block Placer — places a block from its input into the empty space it
 * faces. Slow by hand, faster with power. The companion primitive to the Block
 * Breaker.
 *
 * <p>Slot 0 is the block input (top/sides for hoppers).
 */
public class BlockPlacerBlockEntity extends MachineBlockEntity {
	private static final int INPUT_SLOT = 0;
	private static final int[] INPUTS = {INPUT_SLOT};
	private static final int PLACE_TICKS = 60;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;

	public BlockPlacerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BLOCK_PLACER, pos, state, 1);
	}

	@Override
	protected int[] inputSlots() {
		return INPUTS;
	}

	@Override
	protected int outputSlot() {
		return 0;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return stack.getItem() instanceof BlockItem;
	}

	@Override
	protected int maxProgress() {
		return PLACE_TICKS;
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return INPUTS;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return slot == INPUT_SLOT && isValidInput(stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		ItemStack input = inventory.get(INPUT_SLOT);
		if (!(input.getItem() instanceof BlockItem blockItem)) {
			progress = 0;
			return;
		}
		Direction facing = state.get(FacingMachineBlock.FACING);
		BlockPos target = pos.offset(facing);
		if (!world.getBlockState(target).isReplaceable()) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress >= maxProgress()) {
			progress = 0;
			world.setBlockState(target, blockItem.getBlock().getDefaultState());
			input.decrement(1);
			markDirty();
		}
	}
}
