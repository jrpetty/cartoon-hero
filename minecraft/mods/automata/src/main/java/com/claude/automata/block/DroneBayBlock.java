package com.claude.automata.block;

import com.claude.automata.block.entity.DroneBayBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Drone Bay block. Wrench-click to select it, then wrench-click another bay
 * to make this one ferry its contents there; sneak-wrench-click to unlink.
 * Otherwise behaves as a normal machine inventory (right-click to pull items).
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
		if (!stack.isOf(ModItems.LOGISTICS_WRENCH)) {
			return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
		}
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof DroneBayBlockEntity bay)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (player.isSneaking()) {
			bay.setDestination(null);
			player.sendMessage(Text.literal("Drone Bay unlinked.").formatted(Formatting.RED), false);
			return ItemActionResult.SUCCESS;
		}

		BlockPos selected = DroneBayBlockEntity.getSelection(player.getUuid());
		if (selected == null || selected.equals(pos)) {
			DroneBayBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Drone Bay selected. Wrench-click another bay to send to it.")
					.formatted(Formatting.AQUA), false);
		} else if (world.getBlockEntity(selected) instanceof DroneBayBlockEntity source) {
			source.setDestination(pos);
			DroneBayBlockEntity.deselect(player.getUuid());
			player.sendMessage(Text.literal("Linked: drones will ferry items to this bay.")
					.formatted(Formatting.GREEN), false);
		} else {
			DroneBayBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Drone Bay selected.").formatted(Formatting.AQUA), false);
		}
		return ItemActionResult.SUCCESS;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.DRONE_BAY, (w, p, s, be) -> be.tick(w, p, s));
	}
}
