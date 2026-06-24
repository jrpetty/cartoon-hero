package com.claude.automata.block.entity;

import com.claude.automata.block.FacingMachineBlock;
import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
		super(ModBlockEntities.BLOCK_BREAKER.get(), pos, state, SIZE);
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (!hasEmptySlot()) {
			return;
		}
		Direction facing = state.getValue(FacingMachineBlock.FACING);
		BlockPos target = pos.relative(facing);
		BlockState targetState = world.getBlockState(target);
		if (targetState.isAir() || targetState.getDestroySpeed(world, target) < 0) {
			progress = 0;
			return;
		}

		int step = MachinePower.draw(world, pos, ENERGY_PER_TICK) ? POWERED_STEP : 1;
		progress += step;
		if (progress < BREAK_TICKS) {
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
					Containers.dropItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), leftover);
				}
			}
		}
		setChanged();
	}
}
