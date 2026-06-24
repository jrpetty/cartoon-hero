package com.claude.automata.block;

import com.claude.automata.block.entity.DroneBayBlockEntity;
import com.claude.automata.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
public class DroneBayBlock extends MachineBlock implements BlockEntityProvider {
	public static final MapCodec<DroneBayBlock> CODEC = createCodec(DroneBayBlock::new);

	public DroneBayBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<DroneBayBlock> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DroneBayBlockEntity(pos, state);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof DroneBayBlockEntity bay)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (stack.isOf(ModItems.LOGISTICS_WRENCH)) {
			handleWrench(bay, player, pos, world);
			return ItemActionResult.SUCCESS;
		}
		if (DroneBayBlockEntity.isBattery(stack.getItem())) {
			if (bay.installBattery(stack)) {
				stack.decrement(1);
				player.sendMessage(Text.literal("Battery installed — range " + bay.range() + " blocks.")
						.formatted(Formatting.GREEN), false);
			} else {
				player.sendMessage(Text.literal("A battery is already installed.").formatted(Formatting.YELLOW), false);
			}
			return ItemActionResult.SUCCESS;
		}
		// Otherwise load the payload by hand.
		if (bay.insertFromPlayer(player, hand, stack)) {
			return ItemActionResult.SUCCESS;
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private void handleWrench(DroneBayBlockEntity bay, PlayerEntity player, BlockPos pos, World world) {
		if (player.isSneaking()) {
			bay.setDestination(null);
			player.sendMessage(Text.literal("Drone Bay unlinked.").formatted(Formatting.RED), false);
			return;
		}
		BlockPos selected = DroneBayBlockEntity.getSelection(player.getUuid());
		if (selected == null || selected.equals(pos)) {
			DroneBayBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Drone Bay selected. Wrench another bay to set it as the destination.")
					.formatted(Formatting.AQUA), false);
		} else if (world.getBlockEntity(selected) instanceof DroneBayBlockEntity source) {
			source.setDestination(pos);
			DroneBayBlockEntity.deselect(player.getUuid());
			player.sendMessage(Text.literal("Linked: that bay will send drones here.").formatted(Formatting.GREEN), false);
		} else {
			DroneBayBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Drone Bay selected.").formatted(Formatting.AQUA), false);
		}
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof DroneBayBlockEntity bay) {
			if (player.isSneaking()) {
				int altitude = bay.cycleAltitude();
				player.sendMessage(Text.literal("Cruise altitude: " + altitude).formatted(Formatting.AQUA), false);
			} else {
				String result = bay.send(world);
				player.sendMessage(Text.literal(result).formatted(Formatting.GOLD), false);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}
}
