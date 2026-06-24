package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
		super(ModBlockEntities.MINER.get(), pos, state, SIZE);
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (miningY == UNSET) {
			miningY = pos.getY() - 1;
		}
		// Reached the bottom of the world: nothing left to do.
		if (miningY < world.getMinBuildHeight()) {
			return;
		}
		// Don't mine if there's nowhere to put the drops (back-pressure).
		if (!hasEmptySlot()) {
			return;
		}

		BlockPos target = new BlockPos(pos.getX(), miningY, pos.getZ());
		BlockState targetState = world.getBlockState(target);

		// Unbreakable (e.g. bedrock) stops the drill.
		if (targetState.getDestroySpeed(world, target) < 0) {
			return;
		}
		// Skip air and fluids quickly — one layer per tick, no mining time spent.
		if (targetState.isAir() || !targetState.getFluidState().isEmpty()) {
			miningY--;
			progress = 0;
			setChanged();
			return;
		}

		// Solid block: spend mining time (faster when powered).
		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress < MINE_TICKS) {
			return;
		}
		progress = 0;

		if (world instanceof ServerLevel serverWorld) {
			List<ItemStack> drops = Block.getDrops(targetState, serverWorld, target, null, null,
					new ItemStack(Items.NETHERITE_PICKAXE));
			world.destroyBlock(target, false);
			for (ItemStack drop : drops) {
				ItemStack leftover = addOutput(drop);
				if (!leftover.isEmpty()) {
					// Output is full: spill the remainder into the world rather than void it.
					Containers.dropItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
		}
		miningY--;
		setChanged();
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("MiningY", miningY);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		miningY = nbt.contains("MiningY") ? nbt.getInt("MiningY") : UNSET;
	}
}
