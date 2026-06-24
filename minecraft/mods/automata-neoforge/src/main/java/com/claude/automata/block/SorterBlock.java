package com.claude.automata.block;

import com.claude.automata.block.entity.SorterBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Item Sorter block. Right-click with an item to toggle whether it's sorted
 * (sent to the bottom output); right-click empty-handed for status; sneak-
 * right-click to clear the sort list. Drops its buffered contents when broken.
 */
public class SorterBlock extends BaseEntityBlock {
	public static final MapCodec<SorterBlock> CODEC = simpleCodec(SorterBlock::new);

	public SorterBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<SorterBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SorterBlockEntity(pos, state);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
			boolean added = sorter.toggleSort(stack.getItem());
			player.displayClientMessage(Component.literal((added ? "Now sorting " : "No longer sorting ")
					+ stack.getHoverName().getString()).withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
			if (player.isShiftKeyDown()) {
				sorter.clearSort();
				player.displayClientMessage(Component.literal("Sort list cleared.").withStyle(ChatFormatting.RED), false);
			} else {
				player.displayClientMessage(Component.literal("Sorter: keeping " + sorter.sortCount()
						+ " item type(s) (sorted out the bottom, the rest out the sides).")
						.withStyle(ChatFormatting.GRAY), false);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
				Containers.dropContents(world, pos, sorter);
				world.updateNeighbourForOutputSignal(pos, this);
			}
			super.onRemove(state, world, pos, newState, moved);
		}
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.SORTER.get(), (w, p, s, be) -> be.tick(w, p, s));
	}
}
