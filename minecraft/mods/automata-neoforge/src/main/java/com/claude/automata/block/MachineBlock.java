package com.claude.automata.block;

import com.claude.automata.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Base class for Automata's machine blocks. Provides the manual interaction
 * (right-click with an item to load it, right-click empty-handed to pull the
 * output) and drops the machine's contents when broken.
 *
 * <p>Concrete subclasses supply the block-entity codec, the block entity, and
 * the server-side ticker.
 */
public abstract class MachineBlock extends BaseEntityBlock {
	protected MachineBlock(Properties settings) {
		super(settings);
	}

	// PORT-NOTE: BaseEntityBlock declares `protected abstract MapCodec<? extends BaseEntityBlock> codec();`
	// MachineBlock is abstract, so concrete leaf subclasses supply their own simpleCodec.

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// BaseEntityBlock defaults to INVISIBLE; our machines use a normal model.
		return RenderShape.MODEL;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			// Installing an upgrade module takes priority over loading it as input.
			if (machine.usesUpgrades() && machine.installUpgrade(stack.getItem())) {
				stack.shrink(1);
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Installed upgrade ("
						+ machine.getSpeedUpgrades() + " speed, " + machine.getEfficiencyUpgrades()
						+ " efficiency).").withStyle(net.minecraft.ChatFormatting.GREEN));
				return ItemInteractionResult.SUCCESS;
			}
			if (machine.hasScreen() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				serverPlayer.openMenu(machine);
				return ItemInteractionResult.SUCCESS;
			}
			if (machine.insertFromPlayer(player, hand, stack)) {
				return ItemInteractionResult.SUCCESS;
			}
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			// Sneak pops installed upgrades; a screen machine opens its GUI; others pull output.
			if (player.isShiftKeyDown() && machine.usesUpgrades()) {
				for (ItemStack module : machine.removeUpgrades()) {
					player.getInventory().placeItemBackInInventory(module);
				}
			} else if (machine.hasScreen() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				serverPlayer.openMenu(machine);
			} else {
				machine.extractToPlayer(player);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
				Containers.dropContents(world, pos, machine);
				for (ItemStack module : machine.removeUpgrades()) {
					Block.popResource(world, pos, module);
				}
				world.updateNeighbourForOutputSignal(pos, this);
			}
			super.onRemove(state, world, pos, newState, moved);
		}
	}
}
