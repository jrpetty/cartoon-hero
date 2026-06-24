package com.claude.automata.block;

import com.claude.automata.block.entity.MachineBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base class for Automata's machine blocks. Provides the manual interaction
 * (right-click with an item to load it, right-click empty-handed to pull the
 * output) and drops the machine's contents when broken.
 *
 * <p>Concrete subclasses supply the block-entity codec, the block entity, and
 * the server-side ticker.
 */
public abstract class MachineBlock extends BlockWithEntity {
	protected MachineBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		// BlockWithEntity defaults to INVISIBLE; our machines use a normal model.
		return BlockRenderType.MODEL;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			// Installing an upgrade module takes priority over loading it as input.
			if (machine.usesUpgrades() && machine.installUpgrade(stack.getItem())) {
				stack.decrement(1);
				player.sendMessage(net.minecraft.text.Text.literal("Installed upgrade ("
						+ machine.getSpeedUpgrades() + " speed, " + machine.getEfficiencyUpgrades()
						+ " efficiency).").formatted(net.minecraft.util.Formatting.GREEN), false);
				return ItemActionResult.SUCCESS;
			}
			if (machine.hasScreen() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				serverPlayer.openHandledScreen(machine);
				return ItemActionResult.SUCCESS;
			}
			if (machine.insertFromPlayer(player, hand, stack)) {
				return ItemActionResult.SUCCESS;
			}
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			// Sneak pops installed upgrades; a screen machine opens its GUI; others pull output.
			if (player.isSneaking() && machine.usesUpgrades()) {
				for (ItemStack module : machine.removeUpgrades()) {
					player.getInventory().offerOrDrop(module);
				}
			} else if (machine.hasScreen() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				serverPlayer.openHandledScreen(machine);
			} else {
				machine.extractToPlayer(player);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
				ItemScatterer.spawn(world, pos, machine);
				for (ItemStack module : machine.removeUpgrades()) {
					ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), module);
				}
				world.updateComparators(pos, this);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}
}
