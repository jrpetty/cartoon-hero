package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Auto-Miner — drills straight down on its own, collecting block drops into
 * its output slots (then onward via hoppers). Runs slowly by hand, ~5x faster
 * with adjacent power.
 *
 * <p>It mines the column directly beneath itself: empty space is skipped a
 * block per tick, solid blocks take a full mining cycle, and it stops when it
 * reaches bedrock (or anything unbreakable) or the bottom of the world. Drops
 * are computed as if mined with a pickaxe, so ores yield correctly.
 */
public class MinerBlockEntity extends OutputMachineBlockEntity {
	private static final int SIZE = 9;
	private static final int MINE_TICKS = 200;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;
	private static final int UNSET = Integer.MIN_VALUE;

	private int miningY = UNSET;

	public MinerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MINER, pos, state, SIZE);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (miningY == UNSET) {
			miningY = pos.getY() - 1;
		}
		// Reached the bottom of the world: nothing left to do.
		if (miningY < world.getBottomY()) {
			return;
		}
		// Don't mine if there's nowhere to put the drops (back-pressure).
		if (!hasEmptySlot()) {
			return;
		}

		BlockPos target = new BlockPos(pos.getX(), miningY, pos.getZ());
		BlockState targetState = world.getBlockState(target);

		// Unbreakable (e.g. bedrock) stops the drill.
		if (targetState.getHardness(world, target) < 0) {
			return;
		}
		// Skip air and fluids quickly — one layer per tick, no mining time spent.
		if (targetState.isAir() || !targetState.getFluidState().isEmpty()) {
			miningY--;
			progress = 0;
			markDirty();
			return;
		}

		// Solid block: spend mining time (faster when powered).
		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress < MINE_TICKS) {
			return;
		}
		progress = 0;

		if (world instanceof ServerWorld serverWorld) {
			List<ItemStack> drops = Block.getDroppedStacks(targetState, serverWorld, target, null, null,
					new ItemStack(Items.NETHERITE_PICKAXE));
			world.breakBlock(target, false);
			for (ItemStack drop : drops) {
				ItemStack leftover = addOutput(drop);
				if (!leftover.isEmpty()) {
					// Output is full: spill the remainder into the world rather than void it.
					ItemScatterer.spawn(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
		}
		miningY--;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("MiningY", miningY);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		miningY = nbt.contains("MiningY") ? nbt.getInt("MiningY") : UNSET;
	}
}
