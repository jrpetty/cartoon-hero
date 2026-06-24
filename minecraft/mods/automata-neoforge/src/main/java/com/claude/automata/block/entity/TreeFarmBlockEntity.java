package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

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
		super(ModBlockEntities.TREE_FARM.get(), pos, state, 7);
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
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? OUTPUT : INPUT;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == 0 && isValidInput(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot >= 1 && slot <= 6;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
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

		if (log != null && world instanceof ServerLevel serverWorld) {
			BlockState logState = world.getBlockState(log);
			List<ItemStack> drops = Block.getDrops(logState, serverWorld, log, null, null, ItemStack.EMPTY);
			world.destroyBlock(log, false);
			for (ItemStack drop : drops) {
				ItemStack leftover = addToOutput(drop);
				if (!leftover.isEmpty()) {
					Containers.dropItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
			setChanged();
		} else {
			BlockPos spot = findPlantSpot(world, pos);
			ItemStack sapling = inventory.get(0);
			if (spot != null && sapling.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SaplingBlock) {
				world.setBlockAndUpdate(spot, bi.getBlock().defaultBlockState());
				sapling.shrink(1);
				setChanged();
			}
		}
	}

	private BlockPos findLog(Level world, BlockPos pos) {
		for (int dy = 1; dy <= HEIGHT; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockPos p = new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (world.getBlockState(p).is(BlockTags.LOGS)) {
						return p;
					}
				}
			}
		}
		return null;
	}

	/** An air space above dirt where a sapling can be planted. */
	private BlockPos findPlantSpot(Level world, BlockPos pos) {
		int y = pos.getY();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockPos ground = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
				BlockPos above = ground.above();
				if (world.getBlockState(ground).is(BlockTags.DIRT) && world.getBlockState(above).canBeReplaced()) {
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
					if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, stack)) {
						int space = cur.getMaxStackSize() - cur.getCount();
						int move = Math.min(space, stack.getCount());
						cur.grow(move);
						stack.shrink(move);
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
