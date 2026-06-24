package com.claude.automata.block;

import com.claude.automata.block.entity.DroneBayBlockEntity;
import com.claude.automata.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Drone Bay block.
 * <ul>
 *   <li>Wrench → select this bay, then wrench another to link it as the
 *       destination; sneak-wrench to unlink.</li>
 *   <li>Battery item → install it (sets the drone's range).</li>
 *   <li>Other item → load it into the payload (up to 3 stacks).</li>
 *   <li>Empty hand → <b>send</b> a drone; sneak-empty-hand → cycle cruise altitude.</li>
 * </ul>
 */
public class DroneBayBlock extends MachineBlock implements EntityBlock {
	public static final MapCodec<DroneBayBlock> CODEC = simpleCodec(DroneBayBlock::new);

	public DroneBayBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<DroneBayBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DroneBayBlockEntity(pos, state);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof DroneBayBlockEntity bay)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (stack.is(ModItems.LOGISTICS_WRENCH.get())) {
			handleWrench(bay, player, pos, world);
			return ItemInteractionResult.SUCCESS;
		}
		if (DroneBayBlockEntity.isBattery(stack.getItem())) {
			if (bay.installBattery(stack)) {
				stack.shrink(1);
				player.displayClientMessage(Component.literal("Battery installed — range " + bay.range() + " blocks.")
						.withStyle(ChatFormatting.GREEN), false);
			} else {
				player.displayClientMessage(Component.literal("A battery is already installed.").withStyle(ChatFormatting.YELLOW), false);
			}
			return ItemInteractionResult.SUCCESS;
		}
		// Otherwise load the payload by hand.
		if (bay.insertFromPlayer(player, hand, stack)) {
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private void handleWrench(DroneBayBlockEntity bay, Player player, BlockPos pos, Level world) {
		if (player.isShiftKeyDown()) {
			bay.setDestination(null);
			player.displayClientMessage(Component.literal("Drone Bay unlinked.").withStyle(ChatFormatting.RED), false);
			return;
		}
		BlockPos selected = DroneBayBlockEntity.getSelection(player.getUUID());
		if (selected == null || selected.equals(pos)) {
			DroneBayBlockEntity.select(player.getUUID(), pos);
			player.displayClientMessage(Component.literal("Drone Bay selected. Wrench another bay to set it as the destination.")
					.withStyle(ChatFormatting.AQUA), false);
		} else if (world.getBlockEntity(selected) instanceof DroneBayBlockEntity source) {
			source.setDestination(pos);
			DroneBayBlockEntity.deselect(player.getUUID());
			player.displayClientMessage(Component.literal("Linked: that bay will send drones here.").withStyle(ChatFormatting.GREEN), false);
		} else {
			DroneBayBlockEntity.select(player.getUUID(), pos);
			player.displayClientMessage(Component.literal("Drone Bay selected.").withStyle(ChatFormatting.AQUA), false);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof DroneBayBlockEntity bay) {
			if (player.isShiftKeyDown()) {
				int altitude = bay.cycleAltitude();
				player.displayClientMessage(Component.literal("Cruise altitude: " + altitude).withStyle(ChatFormatting.AQUA), false);
			} else {
				String result = bay.send(world);
				player.displayClientMessage(Component.literal(result).withStyle(ChatFormatting.GOLD), false);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
