package com.claude.automata.block;

import com.claude.automata.block.entity.SorterBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Item Sorter block. Right-click with an item to toggle whether it's sorted
 * (sent to the bottom output); right-click empty-handed for status; sneak-
 * right-click to clear the sort list. Drops its buffered contents when broken.
 */
public class SorterBlock extends BlockWithEntity {
	public static final MapCodec<SorterBlock> CODEC = createCodec(SorterBlock::new);

	public SorterBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<SorterBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SorterBlockEntity(pos, state);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
			boolean added = sorter.toggleSort(stack.getItem());
			player.sendMessage(Text.literal((added ? "Now sorting " : "No longer sorting ")
					+ stack.getName().getString()).formatted(added ? Formatting.GREEN : Formatting.YELLOW), false);
			return ItemActionResult.SUCCESS;
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
			if (player.isSneaking()) {
				sorter.clearSort();
				player.sendMessage(Text.literal("Sort list cleared.").formatted(Formatting.RED), false);
			} else {
				player.sendMessage(Text.literal("Sorter: keeping " + sorter.sortCount()
						+ " item type(s) (sorted out the bottom, the rest out the sides).")
						.formatted(Formatting.GRAY), false);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
				ItemScatterer.spawn(world, pos, sorter);
				world.updateComparators(pos, this);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.SORTER, (w, p, s, be) -> be.tick(w, p, s));
	}
}
