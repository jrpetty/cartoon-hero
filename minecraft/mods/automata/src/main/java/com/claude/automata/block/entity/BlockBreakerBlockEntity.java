package com.claude.automata.block.entity;

import com.claude.automata.block.FacingMachineBlock;
import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Block Breaker — breaks the single block in the direction it faces and
 * collects the drops into its output (computed as if mined with a pickaxe).
 * Slow by hand, faster with power. A primitive for contraptions.
 */
public class BlockBreakerBlockEntity extends OutputMachineBlockEntity {
	private static final int SIZE = 9;
	private static final int BREAK_TICKS = 120;
	private static final int POWERED_STEP = 5;
	private static final int ENERGY_PER_TICK = 10;

	public BlockBreakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BLOCK_BREAKER, pos, state, SIZE);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (!hasEmptySlot()) {
			return;
		}
		Direction facing = state.get(FacingMachineBlock.FACING);
		BlockPos target = pos.offset(facing);
		BlockState targetState = world.getBlockState(target);
		if (targetState.isAir() || targetState.getHardness(world, target) < 0) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress < BREAK_TICKS) {
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
					ItemScatterer.spawn(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
		}
		markDirty();
	}
}
