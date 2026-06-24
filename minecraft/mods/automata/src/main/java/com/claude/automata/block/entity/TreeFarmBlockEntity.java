package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Tree Farm — plants saplings from its input onto dirt around it and chops
 * the logs of grown trees into its output. Slow by hand, faster with power.
 *
 * <p>Place it level with the dirt floor; it plants on a 5x5 area and harvests
 * logs up to {@value #HEIGHT} blocks above. Pair with an Item Collector to catch
 * saplings and apples from the leaves.
 *
 * <p>Slot 0 is the sapling input (top/sides); slots 1-6 are the log output (bottom).
 */
public class TreeFarmBlockEntity extends MachineBlockEntity {
	private static final int[] INPUT = {0};
	private static final int[] OUTPUT = {1, 2, 3, 4, 5, 6};
	private static final int RADIUS = 2;
	private static final int HEIGHT = 16;
	private static final int WORK_TICKS = 100;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;

	public TreeFarmBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TREE_FARM, pos, state, 7);
	}

	@Override
	protected int[] inputSlots() {
		return INPUT;
	}

	@Override
	protected int outputSlot() {
		return OUTPUT[0];
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock;
	}

	@Override
	protected int maxProgress() {
		return WORK_TICKS;
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return side == Direction.DOWN ? OUTPUT : INPUT;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return slot == 0 && isValidInput(stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot >= 1 && slot <= 6;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		BlockPos log = findLog(world, pos);
		boolean canPlant = inventory.get(0).getItem() instanceof BlockItem bi && bi.getBlock() instanceof SaplingBlock
				&& findPlantSpot(world, pos) != null;
		if (log == null && !canPlant) {
			progress = 0;
			return;
		}
		if (log != null && !hasOutputRoom()) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress < WORK_TICKS) {
			return;
		}
		progress = 0;

		if (log != null && world instanceof ServerWorld serverWorld) {
			BlockState logState = world.getBlockState(log);
			List<ItemStack> drops = Block.getDroppedStacks(logState, serverWorld, log, null, null, ItemStack.EMPTY);
			world.breakBlock(log, false);
			for (ItemStack drop : drops) {
				ItemStack leftover = addToOutput(drop);
				if (!leftover.isEmpty()) {
					ItemScatterer.spawn(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
			markDirty();
		} else {
			BlockPos spot = findPlantSpot(world, pos);
			ItemStack sapling = inventory.get(0);
			if (spot != null && sapling.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SaplingBlock) {
				world.setBlockState(spot, bi.getBlock().getDefaultState());
				sapling.decrement(1);
				markDirty();
			}
		}
	}

	private BlockPos findLog(World world, BlockPos pos) {
		for (int dy = 1; dy <= HEIGHT; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockPos p = new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (world.getBlockState(p).isIn(BlockTags.LOGS)) {
						return p;
					}
				}
			}
		}
		return null;
	}

	/** An air space above dirt where a sapling can be planted. */
	private BlockPos findPlantSpot(World world, BlockPos pos) {
		int y = pos.getY();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockPos ground = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
				BlockPos above = ground.up();
				if (world.getBlockState(ground).isIn(BlockTags.DIRT) && world.getBlockState(above).isReplaceable()) {
					return above;
				}
			}
		}
		return null;
	}

	private boolean hasOutputRoom() {
		for (int slot : OUTPUT) {
			if (inventory.get(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private ItemStack addToOutput(ItemStack stack) {
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int slot : OUTPUT) {
				if (stack.isEmpty()) {
					break;
				}
				ItemStack cur = inventory.get(slot);
				if (pass == 0) {
					if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, stack)) {
						int space = cur.getMaxCount() - cur.getCount();
						int move = Math.min(space, stack.getCount());
						cur.increment(move);
						stack.decrement(move);
					}
				} else if (cur.isEmpty()) {
					inventory.set(slot, stack.copyWithCount(stack.getCount()));
					stack.setCount(0);
				}
			}
		}
		return stack;
	}
}
