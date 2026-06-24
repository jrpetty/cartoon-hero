package com.claude.automata.block;

import com.claude.automata.block.entity.RouterBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Logistics Router block. Interaction:
 * <ul>
 *   <li>Right-click with the Logistics Wrench → select this router (then click
 *       destination inventories with the wrench to link them).</li>
 *   <li>Right-click with any other item → toggle that item in the filter.</li>
 *   <li>Right-click empty-handed → show status; sneak-right-click → clear links.</li>
 * </ul>
 */
public class RouterBlock extends BaseEntityBlock {
	public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

	public RouterBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<RouterBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RouterBlockEntity(pos, state);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (stack.is(ModItems.LOGISTICS_WRENCH.get())) {
			RouterBlockEntity.select(player.getUUID(), pos);
			player.displayClientMessage(Component.literal("Router selected. Right-click chests/machines with the wrench to link them.")
					.withStyle(ChatFormatting.AQUA), false);
		} else {
			boolean added = router.toggleFilter(stack.getItem());
			player.displayClientMessage(Component.literal((added ? "Filter: now sending " : "Filter: stopped sending ")
					+ stack.getHoverName().getString()).withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		}
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof RouterBlockEntity router) {
			if (player.isShiftKeyDown()) {
				router.clearLinks();
				player.displayClientMessage(Component.literal("Router links and filter cleared.").withStyle(ChatFormatting.RED), false);
			} else {
				player.displayClientMessage(Component.literal("Router: " + router.destinationCount()
						+ " destination(s), " + router.filterCount() + " filter item(s).")
						.withStyle(ChatFormatting.GRAY), false);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.ROUTER.get(), (w, p, s, be) -> be.tick(w, p, s));
	}
}
